package com.qaestudos.painel.execucao.executor;

import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusTeste;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estratégia Playwright: chama a CLI com dois reporters — "list" (texto
 * para o console ao vivo) e "json" (relatório estruturado num arquivo).
 */
@Component
public class PlaywrightExecutor implements ExecutorFerramenta {

    private final JsonMapper json;

    public PlaywrightExecutor(JsonMapper json) {
        this.json = json;
    }

    @Override
    public TipoProjeto tipo() {
        return TipoProjeto.PLAYWRIGHT;
    }

    @Override
    public List<Etapa> etapas(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        Path cli = projeto.diretorio().resolve("node_modules/@playwright/test/cli.js");
        List<String> cmd = new ArrayList<>(List.of("node", cli.toString(), "test"));
        cmd.addAll(s.specs());
        cmd.add("--project=" + (s.navegador() == null ? "chromium" : s.navegador()));
        cmd.add("--reporter=list,json");
        if (s.abrirNavegador()) cmd.add("--headed");
        if (s.retentativas() > 0) cmd.add("--retries=" + s.retentativas());
        return List.of(new Etapa(cmd, Map.of("PLAYWRIGHT_JSON_OUTPUT_NAME", pasta.resolve("relatorio.json").toString())));
    }

    @Override
    public Leitura ler(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        Path arquivo = pasta.resolve("relatorio.json");
        if (!Files.isRegularFile(arquivo)) {
            return Leitura.falha("O Playwright não gerou o relatório. Veja o log (o Playwright e os navegadores estão instalados?).");
        }
        try {
            return interpretar(json.readTree(Files.readString(arquivo)), projeto.diretorio());
        } catch (IOException | RuntimeException e) {
            return Leitura.falha("Relatório do Playwright ilegível: " + e.getMessage());
        }
    }

    /** Percorre a árvore de suites do relatório JSON (arquivo → describe → teste). */
    static Leitura interpretar(JsonNode raiz, Path dirProjeto) {
        JsonNode config = raiz.path("config");
        String rootDir = Json.texto(config.path("rootDir"));
        Path base = rootDir != null ? Path.of(rootDir) : dirProjeto.resolve("tests");
        List<ResultadoTeste> resultados = new ArrayList<>();
        for (JsonNode suiteArquivo : Json.itens(raiz.path("suites"))) {
            visitar(suiteArquivo, List.of(), Json.texto(suiteArquivo.path("file")), base, dirProjeto, resultados);
        }
        List<String> errosGlobais = Json.itens(raiz.path("errors")).stream()
                .map(e -> Json.semCores(Json.texto(e.path("message")))).toList();
        String erro = resultados.isEmpty() && !errosGlobais.isEmpty() ? String.join("\n\n", errosGlobais) : null;
        String versao = Json.texto(config.path("version"));
        return new Leitura(resultados, versao == null ? "Playwright" : "Playwright " + versao, erro);
    }

    private static void visitar(JsonNode suite, List<String> titulos, String arquivo, Path base, Path dirProjeto, List<ResultadoTeste> saida) {
        String spec = dirProjeto.relativize(base.resolve(arquivo).normalize()).toString().replace('\\', '/');
        for (JsonNode sp : Json.itens(suite.path("specs"))) {
            List<String> titulo = new ArrayList<>(titulos);
            titulo.add(Json.texto(sp.path("title")));
            for (JsonNode t : Json.itens(sp.path("tests"))) {
                List<JsonNode> tentativas = Json.itens(t.path("results"));
                JsonNode ultima = tentativas.isEmpty() ? null : tentativas.getLast();
                StatusTeste status = switch (String.valueOf(Json.texto(t.path("status")))) {
                    case "unexpected" -> StatusTeste.FALHOU;
                    case "skipped" -> StatusTeste.PULADO;
                    default -> tentativas.isEmpty() ? StatusTeste.PULADO : StatusTeste.PASSOU; // expected ou flaky
                };
                String erro = null;
                if (status == StatusTeste.FALHOU && ultima != null) {
                    erro = Json.itens(ultima.path("errors")).stream()
                            .map(e -> Json.semCores(Json.texto(e.path("message"))))
                            .filter(m -> m != null && !m.isBlank())
                            .reduce((a, b) -> a + "\n\n" + b)
                            .orElse("Falha sem mensagem.");
                }
                long duracao = tentativas.stream().mapToLong(x -> x.path("duration").asLong(0)).sum();
                ResultadoTeste r = new ResultadoTeste(spec, String.join(" › ", titulo), status, duracao, erro, ClassificadorErro.classificar(erro));
                // Evidências da última tentativa (screenshot "only-on-failure", trace "retain-on-failure").
                if (status == StatusTeste.FALHOU && ultima != null) {
                    for (JsonNode anexo : Json.itens(ultima.path("attachments"))) {
                        String caminho = Json.texto(anexo.path("path"));
                        if (caminho != null) r.anexar(Path.of(caminho));
                    }
                }
                saida.add(r);
            }
        }
        for (JsonNode filha : Json.itens(suite.path("suites"))) {
            List<String> t = new ArrayList<>(titulos);
            t.add(Json.texto(filha.path("title")));
            visitar(filha, t, arquivo, base, dirProjeto, saida);
        }
    }
}
