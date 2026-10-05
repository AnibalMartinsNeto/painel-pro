package com.qaestudos.painel.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qaestudos.painel.TestcontainersConfiguration;
import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ExecucaoRepository;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.execucao.StatusTeste;
import com.qaestudos.painel.triagem.TriagemRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Publicação no Jira de ponta a ponta (HTTP → serviço → PostgreSQL real),
 * com o JiraCliente simulado: nenhuma issue é criada de verdade.
 */
@SpringBootTest(properties = "painel.seguranca.chave-mestra=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicacaoJiraApiTest {

    @Autowired MockMvcTester mvc;
    @Autowired ExecucaoRepository execucoes;
    @Autowired TriagemRepository triagens;

    @MockitoBean
    JiraCliente jira;

    private Long resultadoId;

    @BeforeEach
    void setUp() {
        triagens.deleteAll();
        execucoes.deleteAll();
        Execucao e = new Execucao("k6", "test", null, Instant.parse("2026-09-29T10:00:00Z"));
        e.adicionarResultado(new ResultadoTeste("tests/login.js", "Login › ok", StatusTeste.FALHOU, 10L,
                "1 de 3 verificações falharam.", "Asserção"));
        e.finalizar(StatusExecucao.FALHOU, Instant.parse("2026-09-29T10:00:05Z"), 5000L);
        resultadoId = execucoes.save(e).getResultados().getFirst().getId();
        given(jira.criarBug(any())).willReturn(new JiraCliente.Issue("DEV-2", "Login falhando", "Bug", null,
                "https://empresa.atlassian.net/browse/DEV-2"));
    }

    private void salvarTriagem() {
        assertThat(mvc.put().uri("/api/triagem/{id}", resultadoId).contentType(MediaType.APPLICATION_JSON).content("""
                {"classificacao":"BUG_APLICACAO","severidade":"CRITICA","titulo":"Login falhando",
                 "esperado":"logar","encontrado":"não loga","passos":["abrir","entrar"]}
                """)).hasStatusOk();
    }

    private MockMvcTester.MockMvcRequestBuilder publicar(String demanda) {
        return mvc.post().uri("/api/triagem/{id}/publicar", resultadoId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"demanda\":\"" + demanda + "\"}");
    }

    @Test
    void publicaOBugComPrioridadeDaSeveridadeELigaADemanda() {
        salvarTriagem();

        assertThat(publicar("dev-1")) // minúsculas: o painel normaliza
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"chave":"DEV-2","url":"https://empresa.atlassian.net/browse/DEV-2","demanda":"DEV-1","aviso":null}
                        """);

        ArgumentCaptor<JiraCliente.NovoBug> bug = ArgumentCaptor.forClass(JiraCliente.NovoBug.class);
        verify(jira).criarBug(bug.capture());
        assertThat(bug.getValue().resumo()).isEqualTo("Login falhando");
        assertThat(bug.getValue().prioridade()).isEqualTo("Highest"); // CRITICA → Highest
        assertThat(bug.getValue().etiquetas()).containsExactly("qa-panel", "k6");
        verify(jira).vincular("DEV-2", "DEV-1");

        // A fila passa a mostrar o bug publicado.
        assertThat(mvc.get().uri("/api/triagem?projeto=k6")).bodyJson()
                .isLenientlyEqualTo("[{\"triagem\":{\"jiraIssue\":\"DEV-2\",\"demanda\":\"DEV-1\"}}]");
    }

    @Test
    void buscarOBugPublicadoMostraOTesteQueOEncontrou() {
        salvarTriagem();
        assertThat(publicar("DEV-1")).hasStatusOk();
        given(jira.buscarIssue("DEV-2")).willReturn(new JiraCliente.Issue("DEV-2", "Login falhando", "Bug", "Aberto",
                "https://empresa.atlassian.net/browse/DEV-2"));

        assertThat(mvc.get().uri("/api/jira/demandas/dev-2?projeto=k6")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"chave":"DEV-2","origem":{"spec":"tests/login.js","teste":"Login › ok","demanda":"DEV-1"}}
                        """);
        // Uma demanda comum (não publicada pelo painel) não tem origem.
        assertThat(mvc.get().uri("/api/jira/demandas/DEV-1?projeto=k6")).bodyJson().extractingPath("$.origem").isNull();
    }

    @Test
    void segundaPublicacaoDoMesmoTesteDevolve409() {
        salvarTriagem();
        assertThat(publicar("DEV-1")).hasStatusOk();

        assertThat(publicar("DEV-1")).hasStatus(409).bodyJson().extractingPath("$.detail").asString().contains("DEV-2");
    }

    @Test
    void semTriagemSalvaNaoPublica() {
        assertThat(publicar("DEV-1")).hasStatus(400).bodyJson().extractingPath("$.detail").asString().contains("Salve a triagem");
        verify(jira, never()).criarBug(any());
    }

    @Test
    void chaveDeDemandaInvalidaERecusada() {
        salvarTriagem();
        assertThat(publicar("login-do-admin")).hasStatus(400);
        verify(jira, never()).criarBug(any());
    }

    @Test
    void seALigacaoFalharOBugFicaRegistradoComAviso() {
        salvarTriagem();
        willThrow(new RuntimeException("link type não existe")).given(jira).vincular(eq("DEV-2"), eq("DEV-1"));

        assertThat(publicar("DEV-1")).hasStatusOk().bodyJson().extractingPath("$.aviso").asString()
                .contains("não foi possível ligá-lo a DEV-1");
        assertThat(mvc.get().uri("/api/jira/bugs?projeto=k6")).bodyJson()
                .isLenientlyEqualTo("[{\"chave\":\"DEV-2\",\"titulo\":\"Login falhando\"}]");
    }
}
