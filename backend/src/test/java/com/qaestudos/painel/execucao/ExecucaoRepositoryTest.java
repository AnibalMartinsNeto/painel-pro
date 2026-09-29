package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Teste da camada de DADOS: sobe só JPA + Flyway contra um PostgreSQL real
 * (Testcontainers). Cada teste roda numa transação desfeita no final, então
 * um teste nunca enxerga os dados do outro.
 *
 * <p>Valida o que só o banco pode validar: a migração do Flyway, o
 * mapeamento das entidades e o SQL das consultas agregadas.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ExecucaoRepositoryTest {

    @Autowired
    ExecucaoRepository repository;

    @Autowired
    TestEntityManager em;

    private Execucao execucao(String projeto, Instant quando, StatusTeste... resultados) {
        Execucao e = new Execucao(projeto, "test", "chrome", quando);
        int i = 0;
        for (StatusTeste s : resultados) {
            e.adicionarResultado(new ResultadoTeste("cypress/e2e/login.cy.js", "Login › teste " + i++, s, 100L,
                    s == StatusTeste.FALHOU ? "AssertionError" : null, s == StatusTeste.FALHOU ? "Asserção" : null));
        }
        e.finalizar(e.getResultados().stream().anyMatch(r -> r.getStatus() == StatusTeste.FALHOU)
                ? StatusExecucao.FALHOU : StatusExecucao.PASSOU, quando.plusSeconds(10), 10_000L);
        return e;
    }

    @Test
    void salvaExecucaoComResultadosEmCascataERecalculaTotais() {
        Execucao salva = repository.save(execucao("cypress", Instant.now(), StatusTeste.PASSOU, StatusTeste.FALHOU, StatusTeste.PULADO));
        em.flush();
        em.clear(); // esquece o cache: a próxima leitura vem de fato do banco

        Execucao lida = repository.buscarComResultados(salva.getId()).orElseThrow();
        assertThat(lida.getResultados()).hasSize(3);
        assertThat(lida.getTotal()).isEqualTo(3);
        assertThat(lida.getAprovados()).isEqualTo(1);
        assertThat(lida.getReprovados()).isEqualTo(1);
        assertThat(lida.getPulados()).isEqualTo(1);
        assertThat(lida.getResultados().get(0).getChave()).isEqualTo("cypress/e2e/login.cy.js › Login › teste 0");
    }

    @Test
    void resumoDoPeriodoSomaSoExecucoesConcluidasDoProjetoEDoPeriodo() {
        Instant agora = Instant.parse("2026-09-15T12:00:00Z");
        repository.save(execucao("cypress", agora, StatusTeste.PASSOU, StatusTeste.PASSOU, StatusTeste.FALHOU));
        repository.save(execucao("cypress", agora, StatusTeste.PASSOU));
        repository.save(execucao("cypress", Instant.parse("2026-08-01T12:00:00Z"), StatusTeste.FALHOU)); // mês anterior
        repository.save(execucao("playwright", agora, StatusTeste.FALHOU)); // outro projeto
        Execucao cancelada = new Execucao("cypress", null, null, agora);
        cancelada.finalizar(StatusExecucao.CANCELADA, agora, 0L);
        repository.save(cancelada);

        ResumoPeriodo r = repository.resumirPeriodo("cypress", Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(r).isEqualTo(new ResumoPeriodo(2, 4, 3, 1));
        assertThat(r.aprovacao()).isEqualTo(75.0);
    }

    @Test
    void contaFalhasPorSpec() {
        Instant agora = Instant.parse("2026-09-15T12:00:00Z");
        repository.save(execucao("cypress", agora, StatusTeste.FALHOU, StatusTeste.FALHOU, StatusTeste.PASSOU));

        assertThat(repository.contarFalhasPorSpec("cypress", Instant.parse("2026-09-01T00:00:00Z")))
                .containsExactly(new FalhasPorSpec("cypress/e2e/login.cy.js", 2));
    }
}
