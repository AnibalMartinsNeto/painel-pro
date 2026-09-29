package com.qaestudos.painel.execucao.executor;

import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusTeste;
import com.qaestudos.painel.projeto.LocalizadorK6;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estratégia k6: um processo por script (o k6 roda um arquivo por vez),
 * cada um exportando o resumo com --summary-export. Cada CHECK e cada
 * THRESHOLD vira um "teste" — assim as falhas de desempenho entram no
 * mesmo histórico dos testes funcionais.
 */
@Component
public class K6Executor implements ExecutorFerramenta {

    private final JsonMapper json;

    public K6Executor(JsonMapper json) {
        this.json = json;
    }

    @Override
    public TipoProjeto tipo() {
        return TipoProjeto.K6;
    }

    private static String binario() {
        return LocalizadorK6.localizar().map(Path::toString).orElse("k6");
    }

    @Override
    public List<Etapa> etapas(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        List<Etapa> etapas = new ArrayList<>();
        Map<String, String> ambiente = Map.of("NO_COLOR", "1", "K6_BROWSER_HEADLESS", s.abrirNavegador() ? "false" : "true");
        for (int i = 0; i < s.specs().size(); i++) {
            etapas.add(new Etapa(
                    List.of(binario(), "run", s.specs().get(i), "--summary-export=" + pasta.resolve("resumo-" + i + ".json")),
                    ambiente));
        }
        return etapas;
    }

    @Override
    public Leitura ler(Projeto projeto, SolicitacaoExecucao s, Path pasta) {
        List<ResultadoTeste> resultados = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        for (int i = 0; i < s.specs().size(); i++) {
            String spec = s.specs().get(i);
            Path arquivo = pasta.resolve("resumo-" + i + ".json");
            if (!Files.isRegularFile(arquivo)) {
                erros.add(spec + ": o k6 terminou sem gerar resumo. Veja o log.");
                continue;
            }
            try {
                resultados.addAll(interpretar(json.readTree(Files.readString(arquivo)), spec));
            } catch (IOException | RuntimeException e) {
                erros.add(spec + ": resumo ilegível (" + e.getMessage() + ")");
            }
        }
        return new Leitura(resultados, versao(), erros.isEmpty() ? null : String.join("\n", erros));
    }

    /** Checks (percorrendo os grupos) e thresholds de um --summary-export. */
    static List<ResultadoTeste> interpretar(JsonNode resumo, String spec) {
        List<ResultadoTeste> saida = new ArrayList<>();
        visitarGrupo(resumo.path("root_group"), List.of(), spec, saida);
        for (var metrica : resumo.path("metrics").properties()) {
            for (var limite : metrica.getValue().path("thresholds").properties()) {
                // No --summary-export, true significa "limite ULTRAPASSADO".
                boolean violado = limite.getValue().asBoolean(false);
                String erro = violado
                        ? "Limite de desempenho violado: %s %s.%nObservado: %s".formatted(metrica.getKey(), limite.getKey(), descrever(metrica.getValue()))
                        : null;
                saida.add(new ResultadoTeste(spec, "Thresholds › " + metrica.getKey() + ": " + limite.getKey(),
                        violado ? StatusTeste.FALHOU : StatusTeste.PASSOU, null, erro, violado ? "Desempenho / Threshold" : null));
            }
        }
        return saida;
    }

    private static void visitarGrupo(JsonNode grupo, List<String> titulos, String spec, List<ResultadoTeste> saida) {
        for (JsonNode c : Json.itens(grupo.path("checks"))) {
            long ok = c.path("passes").asLong(0), falhas = c.path("fails").asLong(0);
            List<String> t = new ArrayList<>(titulos);
            t.add(Json.texto(c.path("name")));
            String erro = falhas > 0 ? "%d de %d verificações falharam.".formatted(falhas, ok + falhas) : null;
            saida.add(new ResultadoTeste(spec, String.join(" › ", t), falhas > 0 ? StatusTeste.FALHOU : StatusTeste.PASSOU,
                    null, erro, falhas > 0 ? "Asserção" : null));
        }
        for (JsonNode g : Json.itens(grupo.path("groups"))) {
            List<String> t = new ArrayList<>(titulos);
            t.add(Json.texto(g.path("name")));
            visitarGrupo(g, t, spec, saida);
        }
    }

    private static String descrever(JsonNode m) {
        if (m.has("p(95)")) {
            return "média %.0fms · p95 %.0fms · máx %.0fms".formatted(m.path("avg").asDouble(), m.path("p(95)").asDouble(), m.path("max").asDouble());
        }
        if (m.has("value")) return String.valueOf(m.path("value").asDouble());
        return m.toString();
    }

    private static String versao() {
        try {
            Process p = new ProcessBuilder(binario(), "version").redirectErrorStream(true).start();
            String saida = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            Matcher m = Pattern.compile("k6(?:\\.exe)? (v[\\d.]+)").matcher(saida);
            return m.find() ? "k6 " + m.group(1) : "k6";
        } catch (IOException e) {
            return "k6";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserva o sinal de interrupção para quem chamou
            return "k6";
        }
    }
}
