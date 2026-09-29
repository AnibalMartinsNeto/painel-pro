package com.qaestudos.painel.execucao.executor;

import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusTeste;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Estratégia Cypress: roda o cypress-runner.cjs e lê o JSON que ele grava. */
@Component
public class CypressExecutor implements ExecutorFerramenta {

    private final JsonMapper json;

    public CypressExecutor(JsonMapper json) {
        this.json = json;
    }

    @Override
    public TipoProjeto tipo() {
        return TipoProjeto.CYPRESS;
    }

    @Override
    public List<Etapa> etapas(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        try (InputStream runner = new ClassPathResource("runners/cypress-runner.cjs").getInputStream()) {
            Path script = pasta.resolve("cypress-runner.cjs");
            Files.copy(runner, script);
            Path config = pasta.resolve("config.json");
            Files.writeString(config, json.writeValueAsString(Map.of(
                    "projeto", projeto.diretorio().toString(),
                    "specs", s.specs(),
                    "navegador", s.navegador() == null ? "electron" : s.navegador(),
                    "abrirNavegador", s.abrirNavegador(),
                    "retentativas", s.retentativas(),
                    "resultado", pasta.resolve("resultado.json").toString())));
            return List.of(new Etapa(List.of("node", script.toString(), config.toString()), Map.of()));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar o runner do Cypress", e);
        }
    }

    @Override
    public Leitura ler(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        Path arquivo = pasta.resolve("resultado.json");
        if (!Files.isRegularFile(arquivo)) {
            return Leitura.falha("O Cypress não gerou resultados. Veja o log da execução.");
        }
        try {
            return interpretar(json.readTree(Files.readString(arquivo)));
        } catch (IOException | RuntimeException e) {
            return Leitura.falha("Resultado do Cypress ilegível: " + e.getMessage());
        }
    }

    /** Converte o retorno do cypress.run(). Estático e puro: testável com um JSON de exemplo. */
    static Leitura interpretar(JsonNode raiz) {
        if (!raiz.path("ok").asBoolean(false)) {
            return Leitura.falha(Json.texto(raiz.path("error")));
        }
        JsonNode r = raiz.path("results");
        if ("failed".equals(Json.texto(r.path("status"))) && r.path("runs").isMissingNode()) {
            return Leitura.falha(Json.texto(r.path("message")));
        }
        List<ResultadoTeste> resultados = new ArrayList<>();
        for (JsonNode run : Json.itens(r.path("runs"))) {
            String spec = Json.texto(run.path("spec").path("relative")).replace('\\', '/');
            for (JsonNode t : Json.itens(run.path("tests"))) {
                List<String> titulo = Json.itens(t.path("title")).stream().map(Json::texto).toList();
                String erro = Json.semCores(Json.texto(t.path("displayError")));
                resultados.add(new ResultadoTeste(
                        spec, String.join(" › ", titulo), status(Json.texto(t.path("state"))),
                        Json.numero(t.path("duration")), erro, ClassificadorErro.classificar(erro)));
            }
        }
        String versao = Json.texto(r.path("cypressVersion"));
        return new Leitura(resultados, versao == null ? null : "Cypress " + versao, null);
    }

    private static StatusTeste status(String state) {
        return switch (state == null ? "" : state) {
            case "passed" -> StatusTeste.PASSOU;
            case "failed" -> StatusTeste.FALHOU;
            case "pending" -> StatusTeste.PENDENTE;
            default -> StatusTeste.PULADO;
        };
    }
}
