package com.qaestudos.painel.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
    void semTokenAvisaOQueFaltaSemChamarOJira() {
        when(config.valor(ChaveConfig.JIRA_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cliente.testarConexao()).hasMessageContaining("falta o campo 'API token'");
        jiraFalso.verify(); // nenhuma requisição foi feita
    }
}
