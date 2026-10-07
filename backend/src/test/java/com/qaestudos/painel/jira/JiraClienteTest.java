package com.qaestudos.painel.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Teste do cliente do Jira SEM chamar o Jira de verdade: o
 * MockRestServiceServer intercepta as requisições do RestClient e devolve
 * respostas pré-definidas. Dá para validar a URL chamada, o cabeçalho de
 * autenticação e o tratamento de cada erro (401, 404).
 */
class JiraClienteTest {

    private MockRestServiceServer jiraFalso;
    private ConfiguracaoService config;
    private JiraCliente cliente;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        jiraFalso = MockRestServiceServer.bindTo(builder).build();
        config = mock(ConfiguracaoService.class);
        when(config.valor(ChaveConfig.JIRA_URL)).thenReturn(Optional.of("https://empresa.atlassian.net"));
        when(config.valor(ChaveConfig.JIRA_EMAIL)).thenReturn(Optional.of("qa@empresa.com"));
        when(config.valor(ChaveConfig.JIRA_TOKEN)).thenReturn(Optional.of("token-secreto"));
        when(config.valor(ChaveConfig.JIRA_PROJETO)).thenReturn(Optional.of("QA"));
        ObjectProvider<RestClient.Builder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
        cliente = new JiraCliente(config, provider);
    }

    private static String basic(String credencial) {
        return "Basic " + Base64.getEncoder().encodeToString(credencial.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void conexaoOkValidaUsuarioEProjetoComAutenticacaoBasic() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/myself"))
                .andExpect(header("Authorization", basic("qa@empresa.com:token-secreto")))
                .andRespond(withSuccess("""
                        {"accountId":"1","displayName":"Aníbal QA","emailAddress":"qa@empresa.com"}
                        """, MediaType.APPLICATION_JSON));
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/project/QA"))
                .andRespond(withSuccess("""
                        {"id":"10000","key":"QA","name":"Qualidade"}
                        """, MediaType.APPLICATION_JSON));

        var resultado = cliente.testarConexao();

        assertThat(resultado).isEqualTo(new JiraCliente.TesteConexao("Aníbal QA", "qa@empresa.com", "QA", "Qualidade"));
        jiraFalso.verify(); // todas as chamadas esperadas aconteceram
    }

    @Test
    void credencialRecusadaViraMensagemClara() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/myself")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.testarConexao())
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("e-mail e o API token");
    }

    @Test
    void projetoInexistenteViraMensagemClara() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/myself"))
                .andRespond(withSuccess("{\"displayName\":\"x\"}", MediaType.APPLICATION_JSON));
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/project/QA")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> cliente.testarConexao()).hasMessageContaining("Projeto 'QA' não encontrado");
    }

    @Test
    void criarBugEnviaCamposNoFormatoDoJiraEDevolveOLink() {
        when(config.valor(ChaveConfig.JIRA_TIPO_ISSUE)).thenReturn(Optional.of("Bug"));
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/issue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.fields.project.key").value("QA"))
                .andExpect(jsonPath("$.fields.issuetype.name").value("Bug"))
                .andExpect(jsonPath("$.fields.summary").value("Login quebrado"))
                .andExpect(jsonPath("$.fields.priority.name").value("High"))
                .andExpect(jsonPath("$.fields.labels[0]").value("qa-panel"))
                .andExpect(jsonPath("$.fields.description.type").value("doc")) // ADF
                .andRespond(withSuccess("{\"id\":\"10005\",\"key\":\"QA-7\"}", MediaType.APPLICATION_JSON));

        var issue = cliente.criarBug(new JiraCliente.NovoBug("Login quebrado",
                new DocumentoAdf().paragrafo("descrição").montar(), "High", java.util.List.of("qa-panel")));

        assertThat(issue.chave()).isEqualTo("QA-7");
        assertThat(issue.url()).isEqualTo("https://empresa.atlassian.net/browse/QA-7");
        jiraFalso.verify();
    }

    @Test
    void campoRecusadoPeloJiraVoltaComOMotivo() {
        when(config.valor(ChaveConfig.JIRA_TIPO_ISSUE)).thenReturn(Optional.of("Bug"));
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/issue")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"errors\":{\"priority\":\"Prioridade inválida\"}}"));

        assertThatThrownBy(() -> cliente.criarBug(new JiraCliente.NovoBug("x", new DocumentoAdf().montar(), "Urgentíssima", java.util.List.of())))
                .hasMessageContaining("Prioridade inválida");
    }

    @Test
    void vincularLigaOBugADemandaComRelates() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/issueLink"))
                .andExpect(jsonPath("$.type.name").value("Relates"))
                .andExpect(jsonPath("$.inwardIssue.key").value("QA-7"))
                .andExpect(jsonPath("$.outwardIssue.key").value("QA-1"))
                .andRespond(withStatus(HttpStatus.CREATED));

        cliente.vincular("QA-7", "QA-1");
        jiraFalso.verify();
    }

    @Test
    void buscarIssueLeResumoTipoEStatus() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/issue/QA-1?fields=summary,issuetype,status"))
                .andRespond(withSuccess("""
                        {"key":"QA-1","fields":{"summary":"Login do admin","issuetype":{"name":"Nova função"},"status":{"name":"Aberto"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.buscarIssue("QA-1")).isEqualTo(new JiraCliente.Issue("QA-1", "Login do admin", "Nova função", "Aberto",
                "https://empresa.atlassian.net/browse/QA-1"));
    }

    @Test
    void ultimasIssuesBuscaPorJqlEMarcaAsDoPainel() {
        jiraFalso.expect(method(HttpMethod.GET))
                .andExpect(request -> {
                    String url = java.net.URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
                    assertThat(url).startsWith("https://empresa.atlassian.net/rest/api/3/search/jql?")
                            .contains("jql=project = \"QA\" ORDER BY created DESC", "maxResults=20");
                })
                .andRespond(withSuccess("""
                        {"issues":[
                          {"key":"QA-7","fields":{"summary":"Login falha","issuetype":{"name":"Bug"},
                            "status":{"name":"Em andamento","statusCategory":{"key":"indeterminate"}},"priority":{"name":"High"},
                            "created":"2026-09-28T14:30:00.000-0300","labels":["qa-panel","cypress"]}},
                          {"key":"QA-6","fields":{"summary":"Pedido do portal","issuetype":{"name":"Task"},
                            "status":{"name":"Aberto","statusCategory":{"key":"new"}},"created":"2026-09-27T09:00:00.000-0300"}}
                        ],"isLast":true}
                        """, MediaType.APPLICATION_JSON));

        var issues = cliente.ultimasIssues(20);

        assertThat(issues).hasSize(2);
        assertThat(issues.get(0)).isEqualTo(new JiraCliente.IssueHistorico("QA-7", "Login falha", "Bug", "Em andamento",
                "indeterminate", "High", java.time.Instant.parse("2026-09-28T17:30:00Z"), true,
                "https://empresa.atlassian.net/browse/QA-7"));
        assertThat(issues.get(1).doPainel()).isFalse();
        assertThat(issues.get(1).prioridade()).isNull();
    }

    @Test
    void buscarDetalheConverteADescricaoAdfEmTexto() {
        jiraFalso.expect(requestTo("https://empresa.atlassian.net/rest/api/3/issue/QA-1?fields=summary,issuetype,status,description"))
                .andRespond(withSuccess("""
                        {"key":"QA-1","fields":{"summary":"Login do admin","issuetype":{"name":"Story"},"status":{"name":"Aberto"},
                         "description":{"type":"doc","version":1,"content":[
                           {"type":"paragraph","content":[{"type":"text","text":"O admin entra com e-mail e senha."}]},
                           {"type":"bulletList","content":[
                             {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"senha errada mostra erro"}]}]},
                             {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"admin vai para /admin/home"}]}]}
                           ]}]}}}
                        """, MediaType.APPLICATION_JSON));

        var d = cliente.buscarDetalhe("QA-1");

        assertThat(d.resumo()).isEqualTo("Login do admin");
        assertThat(d.tipo()).isEqualTo("Story");
        assertThat(d.descricao()).isEqualTo("O admin entra com e-mail e senha.\n- senha errada mostra erro\n- admin vai para /admin/home");
    }

    @Test
    void semTokenAvisaOQueFaltaSemChamarOJira() {
        when(config.valor(ChaveConfig.JIRA_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cliente.testarConexao()).hasMessageContaining("falta o campo 'API token'");
        jiraFalso.verify(); // nenhuma requisição foi feita
    }
}
