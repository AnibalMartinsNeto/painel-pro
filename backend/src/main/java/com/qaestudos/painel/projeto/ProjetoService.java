package com.qaestudos.painel.projeto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regras de negócio sobre projetos: localizar, verificar se estão prontos
 * para rodar e listar os arquivos de teste (specs).
 *
 * <p>Não conhece HTTP nem JSON — isso é papel do Controller. Por isso dá
 * para testar esta classe sem subir servidor nenhum (ver ProjetoServiceTest).
 */
@Service
public class ProjetoService {

    private static final Pattern BASE_URL_DECLARADA = Pattern.compile("baseU[rR][lL]\\s*:\\s*[\"'`]([^\"'`]+)");
    private static final Pattern QUALQUER_URL = Pattern.compile("https?://[^\"'`\\s)]+");

    private final ProjetoRepository repository;
    private final JsonMapper jsonMapper;

    // O Spring já cria um JsonMapper (Jackson 3) configurado; reaproveitamos.
    public ProjetoService(ProjetoRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** Só o campo "scripts" do package.json interessa; o resto é ignorado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PackageJson(Map<String, String> scripts) {}

    /** Scripts do package.json que executam testes, com os specs de cada um. */
    public List<ScriptExecucao> listarScripts(Projeto projeto, List<String> specs) {
        Path arquivo = projeto.diretorio().resolve("package.json");
        if (!Files.isRegularFile(arquivo)) {
            return List.of();
        }
        try {
            PackageJson pkg = jsonMapper.readValue(Files.readString(arquivo), PackageJson.class);
            return pkg.scripts() == null ? List.of() : ScriptsExtrator.extrair(projeto.tipo(), pkg.scripts(), specs);
        } catch (IOException | JacksonException e) {
            // package.json ilegível não deve derrubar a tela: só não há scripts.
            return List.of();
        }
    }

    /**
     * Specs cujo código cita a chave de uma issue (ex.: "DEV-1"). A chave não
     * pode estar colada em outras letras/números: "DEV-1" não casa "DEV-10".
     */
    public List<String> specsQueCitam(Projeto projeto, String chaveIssue) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9-])" + Pattern.quote(chaveIssue) + "(?![0-9])", Pattern.CASE_INSENSITIVE);
        return listarSpecs(projeto).stream().filter(spec -> {
            try {
                return p.matcher(Files.readString(projeto.diretorio().resolve(spec))).find();
            } catch (IOException e) {
                return false;
            }
        }).toList();
    }

    /**
     * Conteúdo do arquivo de regras de negócio do projeto (o .md que a IA
     * usa na triagem), ou vazio se o projeto não tem/o arquivo não existe.
     */
    public Optional<String> regras(Projeto projeto) {
        if (projeto.arquivoRegras() == null) return Optional.empty();
        try {
            return Optional.of(Files.readString(projeto.arquivoRegras())).filter(r -> !r.isBlank());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Só as regras que importam para este spec: as seções ({@code ## ...}) dos
     * módulos que o cobrem, mais as de ambiente. Economiza o prompt da IA e a
     * deixa focada. Sem módulo/seção que bata, devolve o arquivo inteiro.
     */
    public Optional<String> regrasPara(Projeto projeto, String spec) {
        return regras(projeto).map(texto -> {
            List<String> titulos = projeto.modulos().stream()
                    .filter(m -> m.cobre(spec) && m.secaoRegras() != null && !m.secaoRegras().isBlank())
                    .map(Modulo::secaoRegras)
                    .toList();
            if (titulos.isEmpty()) return texto;
            StringBuilder sb = new StringBuilder();
            for (String secao : texto.split("(?m)^(?=## )")) {
                String titulo = secao.lines().findFirst().orElse("").replaceFirst("^##\\s*", "").strip();
                if (titulos.contains(titulo) || titulo.toLowerCase().startsWith("ambiente")) {
                    sb.append(secao.strip()).append("\n\n");
                }
            }
            return sb.isEmpty() ? texto : sb.toString().strip();
        });
    }

    /** Rótulo do módulo do spec (o primeiro que o cobre); sem módulo, deriva do nome do arquivo. */
    public String moduloDe(Projeto projeto, String spec) {
        return projeto.modulos().stream().filter(m -> m.cobre(spec)).map(Modulo::rotulo).findFirst()
                .orElseGet(() -> moduloPeloNome(spec));
    }

    /** "cypress/e2e/login.cy.js" → "Login". */
    static String moduloPeloNome(String spec) {
        String base = spec.substring(spec.lastIndexOf('/') + 1).replaceFirst("\\.(cy|spec|test)?\\.?[jt]sx?$", "");
        return base.isEmpty() ? spec : Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    /** URL da aplicação testada, lida do arquivo de configuração da ferramenta. */
    public Optional<String> baseUrl(Projeto projeto) {
        String arquivo = switch (projeto.tipo()) {
            case CYPRESS -> "cypress.config.js";
            case PLAYWRIGHT -> "playwright.config.js";
            case K6 -> "lib/config.js";
        };
        try {
            String conteudo = Files.readString(projeto.diretorio().resolve(arquivo));
            Matcher declarada = BASE_URL_DECLARADA.matcher(conteudo);
            if (declarada.find()) {
                return Optional.of(declarada.group(1));
            }
            Matcher qualquer = QUALQUER_URL.matcher(conteudo);
            return qualquer.find() ? Optional.of(qualquer.group()) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public List<Projeto> listar() {
        return repository.findAll();
    }

    public Projeto buscar(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto '%s' não existe.".formatted(id)));
    }

    public StatusProjeto status(Projeto projeto) {
        boolean encontrado = Files.isDirectory(projeto.diretorio());
        boolean instalado = encontrado && switch (projeto.tipo()) {
            case CYPRESS -> Files.isDirectory(projeto.diretorio().resolve("node_modules/cypress"));
            case PLAYWRIGHT -> Files.isDirectory(projeto.diretorio().resolve("node_modules/@playwright/test"));
            case K6 -> LocalizadorK6.localizar().isPresent();
        };
        return new StatusProjeto(encontrado, instalado);
    }

    /** Specs do projeto, com caminho relativo à pasta do projeto e barras "/". */
    public List<String> listarSpecs(Projeto projeto) {
        Path raiz = projeto.diretorio().resolve(projeto.pastaSpecs());
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            return arquivos
                    .filter(Files::isRegularFile)
                    .filter(p -> projeto.padraoSpec().matcher(p.getFileName().toString()).matches())
                    .map(p -> projeto.diretorio().relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar specs de " + projeto.id(), e);
        }
    }
}
