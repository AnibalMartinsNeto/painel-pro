package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
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

    @Test
    void execucaoDevFicaForaDoResumoDaFalhaPorSpecEDaPrevisao(@Autowired ResultadoTesteRepository resultados) {
        Instant agora = Instant.parse("2026-09-15T12:00:00Z");
        repository.save(execucao("cypress", agora, StatusTeste.PASSOU, StatusTeste.FALHOU));
        Execucao dev = execucao("cypress", agora.plusSeconds(60), StatusTeste.FALHOU, StatusTeste.FALHOU, StatusTeste.FALHOU);
        dev.marcarComoDev();
        repository.save(dev);
        em.flush();

        assertThat(repository.resumirPeriodo("cypress", Instant.parse("2026-09-01T00:00:00Z"))).isEqualTo(new ResumoPeriodo(1, 2, 1, 1));
        assertThat(repository.contarFalhasPorSpec("cypress", Instant.parse("2026-09-01T00:00:00Z")))
                .containsExactly(new FalhasPorSpec("cypress/e2e/login.cy.js", 1));
        assertThat(repository.countByProjetoIdAndDevFalse("cypress")).isEqualTo(1);
        assertThat(resultados.historicoPorTeste("cypress")).allMatch(h -> h.execucoes() == 1);
        // Ainda aparece no histórico (lista das últimas execuções), com o selo dev.
        assertThat(repository.findTop50ByProjetoIdOrderByIniciadaEmDesc("cypress")).extracting(Execucao::isDev).containsExactly(true, false);
    }

    @Test
    void ultimosResultadosSaoOsDaExecucaoRealMaisRecenteDoSpec(@Autowired ResultadoTesteRepository resultados) {
        repository.save(execucao("cypress", Instant.parse("2026-09-10T12:00:00Z"), StatusTeste.FALHOU, StatusTeste.FALHOU));
        repository.save(execucao("cypress", Instant.parse("2026-09-11T12:00:00Z"), StatusTeste.PASSOU, StatusTeste.FALHOU));
        Execucao dev = execucao("cypress", Instant.parse("2026-09-12T12:00:00Z"), StatusTeste.FALHOU, StatusTeste.PASSOU);
        dev.marcarComoDev(); // mais nova, mas de teste: não conta
        repository.save(dev);
        em.flush();

        var ultimos = resultados.ultimosResultados("cypress", List.of("cypress/e2e/login.cy.js"));

        assertThat(ultimos).extracting(r -> r.getTitulo() + "=" + r.getStatus())
                .containsExactlyInAnyOrder("Login › teste 0=PASSOU", "Login › teste 1=FALHOU");
        assertThat(ultimos).allMatch(r -> r.getQuando().equals(Instant.parse("2026-09-11T12:00:00Z")));
        assertThat(resultados.ultimosResultados("cypress", List.of("outro.cy.js"))).isEmpty();

        // O spec mudou: a execução mais nova só tem 1 teste. O teste removido não volta com resultado antigo.
        repository.save(execucao("cypress", Instant.parse("2026-09-13T12:00:00Z"), StatusTeste.PASSOU));
        em.flush();
        assertThat(resultados.ultimosResultados("cypress", List.of("cypress/e2e/login.cy.js")))
                .extracting(r -> r.getTitulo()).containsExactly("Login › teste 0");
    }

    @Test
    void previsaoUsaAMediaDeCadaSpecEOTempoFixoDasExecucoesReais() {
        Instant agora = Instant.parse("2026-09-15T12:00:00Z");
        // execucao(): 2 testes de 100ms cada no mesmo spec (= 200ms por execução) e duração total de 10s.
        repository.save(execucao("cypress", agora, StatusTeste.PASSOU, StatusTeste.PASSOU));
        repository.save(execucao("cypress", agora.plusSeconds(60), StatusTeste.PASSOU, StatusTeste.FALHOU));
        em.flush();

        assertThat(repository.mediaPorSpec("cypress")).singleElement().satisfies(d -> {
            assertThat(d.getSpec()).isEqualTo("cypress/e2e/login.cy.js");
            assertThat(d.getMediaMs()).isEqualTo(200L);
            assertThat(d.getAmostras()).isEqualTo(2L);
        });
        assertThat(repository.mediaTempoFixo("cypress")).isEqualTo(9_800L); // 10s - 200ms
    }

    @Test
    void historicoPorTesteContaFalhasEAprovacoesDeCadaTeste(@Autowired ResultadoTesteRepository resultados) {
        // "teste 0" passa e depois falha (instável); "teste 1" sempre falha; "teste 2" sempre passa.
        repository.save(execucao("cypress", Instant.parse("2026-09-10T12:00:00Z"), StatusTeste.PASSOU, StatusTeste.FALHOU, StatusTeste.PASSOU));
        repository.save(execucao("cypress", Instant.parse("2026-09-11T12:00:00Z"), StatusTeste.FALHOU, StatusTeste.FALHOU, StatusTeste.PASSOU));
        repository.save(execucao("playwright", Instant.parse("2026-09-11T12:00:00Z"), StatusTeste.FALHOU)); // outro projeto
        em.flush();

        var porTitulo = resultados.historicoPorTeste("cypress").stream()
                .collect(java.util.stream.Collectors.toMap(HistoricoTeste::titulo, h -> h));

        assertThat(porTitulo).hasSize(3);
        assertThat(porTitulo.get("Login › teste 0")).extracting(HistoricoTeste::execucoes, HistoricoTeste::falhas, HistoricoTeste::instavel)
                .containsExactly(2L, 1L, true);
        assertThat(porTitulo.get("Login › teste 1")).extracting(HistoricoTeste::falhas, HistoricoTeste::instavel).containsExactly(2L, false);
        assertThat(porTitulo.get("Login › teste 2").falhas()).isZero();

        var falhas = resultados.falhasRecentes("cypress");
        assertThat(falhas).hasSize(3);
        assertThat(falhas.get(0).getExecucao().getIniciadaEm()).isEqualTo(Instant.parse("2026-09-11T12:00:00Z"));
    }
}
