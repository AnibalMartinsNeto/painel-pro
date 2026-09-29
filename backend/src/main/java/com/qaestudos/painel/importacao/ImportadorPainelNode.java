package com.qaestudos.painel.importacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ExecucaoRepository;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.execucao.StatusTeste;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importa o histórico gravado pelo painel Node (Painel/data/runs/*.json)
 * para o PostgreSQL.
 *
 * <p>É IDEMPOTENTE: rodar duas vezes não duplica nada, porque cada execução
 * guarda o id de origem (coluna única {@code origem}) e as já importadas
 * são puladas. Idempotência é o que torna uma operação segura de repetir.
 */
@Service
public class ImportadorPainelNode {

    private final ExecucaoRepository repository;
    private final JsonMapper jsonMapper;
    private final Path pasta;

    public ImportadorPainelNode(
            ExecucaoRepository repository,
            JsonMapper jsonMapper,
            @Value("${painel.importacao.pasta-painel-node:../../Painel/data/runs}") String pasta) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
    }

    public record Resultado(int importadas, int ignoradas, List<String> erros) {}

    // --- formato do JSON do painel Node (só os campos usados) ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RunNode(String id, String startedAt, String finishedAt, String status, Options options,
                   Stats stats, String browser, String toolVersion, String cypressVersion, String error, List<SpecNode> specs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Options(String project, String preset, String browser) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stats(Long duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SpecNode(String spec, List<TestNode> tests) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestNode(List<String> title, String state, Double duration, String error, String errorType) {}

    /**
     * {@code @Transactional}: tudo ou nada. Se algo quebrar no meio, o banco
     * desfaz (rollback) o que já tinha sido inserido nesta chamada.
     */
    @Transactional
    public Resultado importar() {
        if (!Files.isDirectory(pasta)) {
            return new Resultado(0, 0, List.of("Pasta não encontrada: " + pasta));
        }
        int importadas = 0, ignoradas = 0;
        List<String> erros = new ArrayList<>();
        for (Path arquivo : listarRuns()) {
            try {
                RunNode run = jsonMapper.readValue(Files.readString(arquivo), RunNode.class);
                if (run.id() == null || run.startedAt() == null || repository.existsByOrigem(run.id())) {
                    ignoradas++;
                    continue;
                }
                repository.save(converter(run));
                importadas++;
            } catch (IOException | RuntimeException e) { // JacksonException (Jackson 3) já é RuntimeException
                erros.add(arquivo.getFileName() + ": " + e.getMessage());
            }
        }
        return new Resultado(importadas, ignoradas, erros);
    }

    private List<Path> listarRuns() {
        // Só os <id>.json da raiz; ignora .log, .tmp e as pastas de screenshots.
        try (Stream<Path> s = Files.list(pasta)) {
            return s.filter(p -> p.getFileName().toString().matches("[\\w-]+\\.json")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao listar " + pasta, e);
        }
    }

    static Execucao converter(RunNode run) {
        Options o = run.options() != null ? run.options() : new Options(null, null, null);
        Execucao e = new Execucao(o.project() != null ? o.project() : "cypress", o.preset(), o.browser(), Instant.parse(run.startedAt()));
        e.definirOrigem(run.id());
        String versao = run.toolVersion() != null ? run.toolVersion() : run.cypressVersion() != null ? "Cypress " + run.cypressVersion() : null;
        e.definirVersaoFerramenta(versao);
        if (run.error() != null) {
            e.registrarErro(run.error());
        }
        for (SpecNode spec : run.specs() == null ? List.<SpecNode>of() : run.specs()) {
            for (TestNode t : spec.tests() == null ? List.<TestNode>of() : spec.tests()) {
                e.adicionarResultado(new ResultadoTeste(
                        spec.spec(),
                        String.join(" › ", t.title()),
                        statusTeste(t.state()),
                        t.duration() == null ? null : Math.round(t.duration()),
                        t.error(),
                        t.errorType()));
            }
        }
        Instant fim = run.finishedAt() != null ? Instant.parse(run.finishedAt()) : null;
        Long duracao = run.stats() != null ? run.stats().duration() : null;
        e.finalizar(statusExecucao(run.status()), fim, duracao);
        return e;
    }

    private static StatusExecucao statusExecucao(String s) {
        return switch (s == null ? "" : s) {
            case "passed" -> StatusExecucao.PASSOU;
            case "failed" -> StatusExecucao.FALHOU;
            case "cancelled" -> StatusExecucao.CANCELADA;
            default -> StatusExecucao.ERRO;
        };
    }

    private static StatusTeste statusTeste(String s) {
        return switch (s == null ? "" : s) {
            case "passed" -> StatusTeste.PASSOU;
            case "failed" -> StatusTeste.FALHOU;
            case "pending" -> StatusTeste.PENDENTE;
            default -> StatusTeste.PULADO;
        };
    }
}
