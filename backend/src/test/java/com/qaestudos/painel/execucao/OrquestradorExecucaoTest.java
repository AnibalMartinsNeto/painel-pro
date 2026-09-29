package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.common.ConflitoException;
import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.execucao.executor.ExecutorFerramenta;
import com.qaestudos.painel.execucao.executor.SolicitacaoExecucao;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.projeto.StatusProjeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Teste de unidade do orquestrador com uma FERRAMENTA FALSA: ela executa
 * um processo real (o próprio java -version, que existe em qualquer
 * máquina que roda estes testes) e devolve resultados pré-definidos.
 * Serviço de projetos e gravação no banco são mocks (Mockito).
 */
class OrquestradorExecucaoTest {

    private static final String JAVA = ProcessHandle.current().info().command().orElse("java");

    private final Projeto projeto = new Projeto("cypress", "Cypress", TipoProjeto.CYPRESS, Path.of(".").toAbsolutePath(),
            "cypress/e2e", Pattern.compile(".*"), List.of("electron"));
    private final SolicitacaoExecucao pedido = new SolicitacaoExecucao("cypress", "test", List.of("a.cy.js"), "electron", 0, false);

    private ProjetoService projetos;
    private ExecucaoGravacao gravacao;
    private FerramentaFalsa ferramenta;
    private OrquestradorExecucao orquestrador;

    /** Estratégia falsa: pode "segurar" a execução num latch para testar concorrência. */
    static class FerramentaFalsa implements ExecutorFerramenta {
        final CountDownLatch liberar = new CountDownLatch(1);
        volatile boolean segurar;
        volatile List<ResultadoTeste> resultados = List.of(
                new ResultadoTeste("a.cy.js", "passa", StatusTeste.PASSOU, 10L, null, null),
                new ResultadoTeste("a.cy.js", "falha", StatusTeste.FALHOU, 10L, "AssertionError", "Asserção"));

        public TipoProjeto tipo() { return TipoProjeto.CYPRESS; }

        public List<Etapa> etapas(Projeto p, SolicitacaoExecucao s, Path pasta) {
            if (segurar) {
                try { liberar.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return List.of(new Etapa(List.of(JAVA, "-version"), Map.of()));
        }

        public Leitura ler(Projeto p, SolicitacaoExecucao s, Path pasta) {
            return new Leitura(resultados, "Falsa 1.0", null);
        }
    }

    @BeforeEach
    void setUp() {
        projetos = mock(ProjetoService.class);
        gravacao = mock(ExecucaoGravacao.class);
        ferramenta = new FerramentaFalsa();
        when(projetos.buscar("cypress")).thenReturn(projeto);
        when(projetos.status(projeto)).thenReturn(new StatusProjeto(true, true));
        when(projetos.listarSpecs(projeto)).thenReturn(List.of("a.cy.js", "b.cy.js"));
        when(gravacao.criar(any())).thenAnswer(inv -> execucaoComId(42L));
        orquestrador = new OrquestradorExecucao(projetos, gravacao, List.of(ferramenta));
    }

    @AfterEach
    void tearDown() {
        ferramenta.liberar.countDown();
        orquestrador.desligar();
    }

    private static Execucao execucaoComId(Long id) {
        Execucao e = new Execucao("cypress", "test", "electron", Instant.now());
        try {
            var campo = Execucao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(e, id); // num teste de unidade não há banco para gerar o id
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    @Test
    void executaOProcessoGravaResultadosEOLog() {
        orquestrador.iniciar(pedido);

        ArgumentCaptor<String> log = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ResultadoTeste>> resultados = ArgumentCaptor.forClass(List.class);
        verify(gravacao, timeout(10_000)).concluir(eq(42L), eq(StatusExecucao.FALHOU), resultados.capture(),
                eq("Falsa 1.0"), isNull(), log.capture());

        assertThat(resultados.getValue()).hasSize(2);
        assertThat(log.getValue()).contains("[painel] ▶").containsIgnoringCase("version"); // saída real do processo
        assertThat(orquestrador.emAndamento()).isEmpty(); // liberou a vez
    }

    @Test
    void semResultadosAExecucaoTerminaComoErro() {
        ferramenta.resultados = List.of();
        orquestrador.iniciar(pedido);

        verify(gravacao, timeout(10_000)).concluir(eq(42L), eq(StatusExecucao.ERRO), anyList(), anyString(),
                eq("A execução terminou sem nenhum resultado de teste."), anyString());
    }

    @Test
    void segundaExecucaoSimultaneaERecusadaCom409() {
        ferramenta.segurar = true;
        orquestrador.iniciar(pedido);

        assertThat(orquestrador.emAndamento()).contains(42L);
        assertThatThrownBy(() -> orquestrador.iniciar(pedido)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void cancelarMarcaComoCancelada() {
        ferramenta.segurar = true;
        orquestrador.iniciar(pedido);

        orquestrador.cancelar(42L);
        ferramenta.liberar.countDown();

        verify(gravacao, timeout(10_000)).concluir(eq(42L), eq(StatusExecucao.CANCELADA), anyList(), isNull(), anyString(), anyString());
    }

    @Test
    void specInexistenteERecusadoAntesDeGravarQualquerCoisa() {
        var invalido = new SolicitacaoExecucao("cypress", null, List.of("nao-existe.cy.js"), "electron", 0, false);

        assertThatThrownBy(() -> orquestrador.iniciar(invalido))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("nao-existe.cy.js");
        verify(gravacao, timeout(0).times(0)).criar(any());
    }

    @Test
    void navegadorNaoSuportadoERecusado() {
        var invalido = new SolicitacaoExecucao("cypress", null, List.of("a.cy.js"), "safari", 0, false);
        assertThatThrownBy(() -> orquestrador.iniciar(invalido)).hasMessageContaining("safari");
    }
}
