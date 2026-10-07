package com.qaestudos.painel.execucao;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de consulta do histórico de execuções.
 *
 * <p>{@code @Transactional(readOnly = true)}: cada método roda dentro de
 * uma transação de leitura — a conexão com o banco é aberta no início e
 * devolvida no fim, e o Hibernate pode otimizar sabendo que nada será
 * gravado.
 */
@Service
@Transactional(readOnly = true)
public class ExecucaoService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");


    private final ExecucaoRepository repository;
    private final EvidenciaRepository evidencias;
    private final ProjetoService projetoService;
    private final Clock clock;

    public ExecucaoService(ExecucaoRepository repository, EvidenciaRepository evidencias, ProjetoService projetoService, Clock clock) {
        this.repository = repository;
        this.evidencias = evidencias;
        this.projetoService = projetoService;
        this.clock = clock;
    }

    public List<Execucao> listar(String projetoId) {
        projetoService.buscar(projetoId); // 404 se o projeto não existir
        return repository.findTop50ByProjetoIdOrderByIniciadaEmDesc(projetoId);
    }

    /** Evidências de uma execução, agrupadas por resultado (tela de detalhe). */
    public Map<Long, List<Evidencia>> evidencias(Long execucaoId) {
        return evidencias.findByResultadoExecucaoIdOrderByIdAsc(execucaoId).stream()
                .collect(Collectors.groupingBy(ev -> ev.getResultado().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    public Execucao buscar(Long id) {
        return repository.buscarComResultados(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução %d não existe.".formatted(id)));
    }

    /** Números da Visão geral: mês corrente, falhas por módulo e última execução de cada script. */
    public ResumoProjeto resumir(String projetoId) {
        Projeto projeto = projetoService.buscar(projetoId);
        YearMonth mes = YearMonth.now(clock.withZone(FUSO));
        Instant inicioMes = mes.atDay(1).atStartOfDay(FUSO).toInstant();

        ResumoPeriodo periodo = repository.resumirPeriodo(projetoId, inicioMes);

        // Agrupa as falhas por módulo (vários specs podem cair no mesmo módulo).
        Map<String, Long> porModulo = new LinkedHashMap<>();
        for (FalhasPorSpec f : repository.contarFalhasPorSpec(projetoId, inicioMes)) {
            porModulo.merge(projetoService.moduloDe(projeto, f.spec()), f.falhas(), Long::sum);
        }
        List<ResumoProjeto.FalhasModulo> modulos = porModulo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ResumoProjeto.FalhasModulo(
                        e.getKey(), e.getValue(), Math.round(e.getValue() * 100.0 / Math.max(1, periodo.reprovados()))))
                .toList();

        Optional<Execucao> ultima = repository.findFirstByProjetoIdAndDevFalseAndStatusInOrderByIniciadaEmDesc(
                projetoId, EnumSet.of(StatusExecucao.PASSOU, StatusExecucao.FALHOU));

        Map<String, Execucao> porScript = new LinkedHashMap<>();
        for (var script : projetoService.listarScripts(projeto, projetoService.listarSpecs(projeto))) {
            repository.findFirstByProjetoIdAndDevFalseAndScriptOrderByIniciadaEmDesc(projetoId, script.nome())
                    .ifPresent(e -> porScript.put(script.nome(), e));
        }

        return new ResumoProjeto(mes, periodo, modulos, ultima.orElse(null), porScript);
    }


    /**
     * Previsão de tempo: média de cada spec nas execuções reais + o tempo fixo
     * médio de uma execução. O front soma os specs escolhidos (eles rodam em
     * sequência) e só mostra a previsão se TODOS tiverem histórico.
     */
    public record Duracoes(long tempoFixoMs, Map<String, Long> mediaPorSpecMs) {}

    public Duracoes duracoes(String projetoId) {
        projetoService.buscar(projetoId); // 404 se o projeto não existir
        Map<String, Long> porSpec = new LinkedHashMap<>();
        for (var d : repository.mediaPorSpec(projetoId)) porSpec.put(d.getSpec(), d.getMediaMs());
        return new Duracoes(repository.mediaTempoFixo(projetoId), porSpec);
    }
}
