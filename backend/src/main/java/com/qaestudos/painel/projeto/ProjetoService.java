package com.qaestudos.painel.projeto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            case K6 -> k6Instalado();
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

    // O k6 é um executável, não um pacote npm: procura no PATH e na pasta
    // padrão de instalação do winget.
    private boolean k6Instalado() {
        String programFiles = Objects.requireNonNullElse(System.getenv("ProgramFiles"), "C:\\Program Files");
        Stream<Path> candidatos = Stream.concat(
                Stream.of(Path.of(programFiles, "k6", "k6.exe")),
                Arrays.stream(Objects.requireNonNullElse(System.getenv("PATH"), "").split(java.io.File.pathSeparator))
                        .filter(s -> !s.isBlank())
                        .flatMap(dir -> Stream.of(Path.of(dir, "k6.exe"), Path.of(dir, "k6"))));
        return candidatos.anyMatch(Files::isRegularFile);
    }
}
