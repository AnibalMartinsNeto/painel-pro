package com.qaestudos.painel.triagem.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Contrato HTTP com cada provedor de IA, sem gastar créditos nem depender
 * da internet: o MockRestServiceServer faz o papel do Google/Anthropic.
 */
class ClientesIaTest {

    private MockRestServiceServer servidor;
    private ObjectProvider<RestClient.Builder> provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(builder);
    }

    @Nested
    class Gemini {

        private GeminiCliente cliente;
        private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-teste:generateContent";

        @BeforeEach
        void setUp() {
            cliente = new GeminiCliente(provider);
            cliente.esperaEntreTentativas = Duration.ZERO; // não esperar de verdade no teste
        }

        @Test
        void enviaPromptPedindoJsonELeSoAsPartesDeTexto() {
            servidor.expect(requestTo(URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("x-goog-api-key", "chave-g"))
                    .andExpect(jsonPath("$.contents[0].parts[0].text").value("meu prompt"))
                    .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                    .andRespond(withSuccess("""
                            {"modelVersion":"gemini-3.8-flash","candidates":[{"content":{"parts":[
                              {"text":"pensando...","thought":true},{"text":"{\\"titulo\\":\\"x\\"}"}]}}]}
                            """, MediaType.APPLICATION_JSON));

            var r = cliente.gerar("meu prompt", "gemini-teste", "chave-g");

            assertThat(r.texto()).isEqualTo("{\"titulo\":\"x\"}");
            assertThat(r.modelo()).isEqualTo("gemini-3.8-flash");
            servidor.verify();
        }

        @Test
        void sobrecarga503TentaDeNovoEFunciona() {
            servidor.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
            servidor.expect(once(), requestTo(URL)).andRespond(withSuccess("""
                    {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
                    """, MediaType.APPLICATION_JSON));

            assertThat(cliente.gerar("p", "gemini-teste", "k").texto()).isEqualTo("ok");
            servidor.verify();
        }

        @Test
        void chaveInvalidaFalhaNaHoraSemRepetir() {
            servidor.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":{\"message\":\"API key not valid\"}}").contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> cliente.gerar("p", "gemini-teste", "errada"))
                    .isInstanceOf(IaIndisponivelException.class)
                    .hasMessageContaining("401")
                    .hasMessageContaining("API key not valid");
            servidor.verify(); // exatamente UMA chamada
        }

        @Test
        void desisteDepoisDeQuatroTentativas() {
            servidor.expect(twice(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
            servidor.expect(twice(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> cliente.gerar("p", "gemini-teste", "k")).hasMessageContaining("429");
            servidor.verify();
        }
    }

    @Nested
    class Anthropic {

        @Test
        void usaCabecalhosDaAnthropicEJuntaOsBlocosDeTexto() {
            var cliente = new AnthropicCliente(provider);
            servidor.expect(requestTo("https://api.anthropic.com/v1/messages"))
                    .andExpect(header("x-api-key", "chave-a"))
                    .andExpect(header("anthropic-version", "2023-06-01"))
                    .andExpect(jsonPath("$.model").value("claude-teste"))
                    .andExpect(jsonPath("$.messages[0].content").value("prompt"))
                    .andRespond(withSuccess("""
                            {"model":"claude-teste","content":[{"type":"text","text":"parte 1 "},{"type":"text","text":"parte 2"}]}
                            """, MediaType.APPLICATION_JSON));

            var r = cliente.gerar("prompt", "claude-teste", "chave-a");

            assertThat(r.texto()).isEqualTo("parte 1 parte 2");
            servidor.verify();
        }
    }
}
