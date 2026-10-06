package com.qaestudos.painel.execucao;

import com.qaestudos.painel.execucao.executor.SolicitacaoExecucao;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravações no banco feitas pelo orquestrador, cada uma na SUA transação.
 *
 * <p>Por que uma classe separada? O {@code @Transactional} do Spring
 * funciona por um "proxy" que envolve o objeto: só vale quando o método é
 * chamado de FORA da classe. O orquestrador roda numa thread própria e
 * chama estes métodos — assim cada gravação abre e fecha a sua transação.
 */
@Service
@Transactional
public class ExecucaoGravacao {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoGravacao.class);

    private final ExecucaoRepository repository;
    private final Clock clock;

    public ExecucaoGravacao(ExecucaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Execucao criar(SolicitacaoExecucao s) {
        Execucao e = new Execucao(s.projetoId(), s.script(), s.navegador(), clock.instant());
        if (s.dev()) e.marcarComoDev();
        return repository.save(e);
    }

    public Execucao concluir(Long id, StatusExecucao status, List<ResultadoTeste> resultados, String versao, String erro, String logTexto) {
        Execucao e = repository.findById(id).orElseThrow();
        resultados.forEach(e::adicionarResultado);
        e.definirVersaoFerramenta(versao);
        if (erro != null) e.registrarErro(erro);
        e.registrarLog(logTexto);
        e.finalizar(status, clock.instant(), Duration.between(e.getIniciadaEm(), clock.instant()).toMillis());
        return e; // dentro da transação: o Hibernate grava as mudanças sozinho no commit
    }

    /**
     * Se o servidor caiu no meio de uma execução, ela ficaria "EM_ANDAMENTO"
     * para sempre. Ao subir, marca essas órfãs como ERRO.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void encerrarOrfas() {
        for (Execucao e : repository.findByStatus(StatusExecucao.EM_ANDAMENTO)) {
            e.registrarErro("Execução interrompida: o servidor foi reiniciado durante a execução.");
            e.finalizar(StatusExecucao.ERRO, clock.instant(), null);
            log.warn("Execução {} estava em andamento ao iniciar; marcada como ERRO.", e.getId());
        }
    }
}
