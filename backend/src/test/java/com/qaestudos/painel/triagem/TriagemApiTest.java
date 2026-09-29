package com.qaestudos.painel.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.qaestudos.painel.TestcontainersConfiguration;
import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ExecucaoRepository;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.execucao.StatusTeste;
import com.qaestudos.painel.triagem.ia.GeminiCliente;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Triagem de ponta a ponta: HTTP → serviço → consulta nativa no
 * PostgreSQL real → IA (simulada com @MockitoBean) → gravação. Só o
 * Gemini é falso; todo o resto é o código de verdade.
 */
@SpringBootTest(properties = "painel.seguranca.chave-mestra=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TriagemApiTest {

    @Autowired MockMvcTester mvc;
    @Autowired ExecucaoRepository execucoes;
    @Autowired TriagemRepository triagens;
    @Autowired ConfiguracaoService config;

    @MockitoBean
    GeminiCliente gemini; // substitui o cliente real: nenhuma chamada sai para a internet

    private Long resultadoQueFalha;

    private Execucao execucao(Instant quando, StatusTeste login, StatusTeste checkout) {
        Execucao e = new Execucao("k6", "test", null, quando);
        e.adicionarResultado(new ResultadoTeste("tests/login.js", "Login › ok", login, 10L,
                login == StatusTeste.FALHOU ? "1 de 3 verificações falharam." : null, login == StatusTeste.FALHOU ? "Asserção" : null));
        e.adicionarResultado(new ResultadoTeste("tests/checkout.js", "Checkout › ok", checkout, 10L,
                checkout == StatusTeste.FALHOU ? "timeout" : null, null));
        e.finalizar(StatusExecucao.FALHOU, quando.plusSeconds(5), 5000L);
        return execucoes.save(e);
    }

    @BeforeEach
    void setUp() {
        triagens.deleteAll();
        execucoes.deleteAll();
        // Ontem: login passou, checkout falhou. Hoje: login falhou, checkout voltou a passar.
        execucao(Instant.parse("2026-09-27T10:00:00Z"), StatusTeste.PASSOU, StatusTeste.FALHOU);
        Execucao hoje = execucao(Instant.parse("2026-09-28T10:00:00Z"), StatusTeste.FALHOU, StatusTeste.PASSOU);
        resultadoQueFalha = hoje.getResultados().getFirst().getId();
        config.definir(ChaveConfig.IA_PROVEDOR, "gemini");
        config.definir(ChaveConfig.IA_GEMINI_CHAVE, "chave-teste");
        given(gemini.id()).willReturn("gemini");
    }

    @Test
    void filaTemSoOQueAindaFalhaNaExecucaoMaisRecente() {
        assertThat(mvc.get().uri("/api/triagem?projeto=k6"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"spec":"tests/login.js","titulo":"Login › ok","tipoErro":"Asserção","triagem":null}]
                        """); // checkout voltou a passar: saiu da fila
    }

    @Test
    void analisarGeraRascunhoComAIaEAClassificacaoFicaSoComoSugestao() {
        given(gemini.gerar(anyString(), anyString(), anyString())).willReturn(new ProvedorIa.Resposta("""
                {"titulo":"Checks de login falhando","classificacao":"BUG_APLICACAO","severidade":"ALTA",
                 "esperado":"todos os checks passam","encontrado":"1 de 3 falhou","passos":["Rodar o smoke"],"analise":"Regressão."}
                """, "gemini-fake"));

        assertThat(mvc.post().uri("/api/triagem/{id}/analisar", resultadoQueFalha))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"titulo":"Checks de login falhando","classificacao":null,"classificacaoSugerida":"BUG_APLICACAO",
                         "severidade":"ALTA","passos":["Rodar o smoke"],"origemRascunho":"IA","modelo":"gemini-fake"}
                        """);
    }

    @Test
    void salvarRevisaoClassificaEApareceNaFila() {
        assertThat(mvc.put().uri("/api/triagem/{id}", resultadoQueFalha).contentType(MediaType.APPLICATION_JSON).content("""
                {"classificacao":"FALHA_AUTOMACAO","severidade":"BAIXA","titulo":"Seletor frágil","passos":["a","b"]}
                """)).hasStatusOk();

        assertThat(mvc.get().uri("/api/triagem?projeto=k6"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"titulo":"Login › ok","triagem":{"classificacao":"FALHA_AUTOMACAO","titulo":"Seletor frágil","passos":["a","b"]}}]
                        """);
    }

    @Test
    void falhaDaIaVira502ComMensagem() {
        given(gemini.gerar(anyString(), anyString(), anyString()))
                .willThrow(new com.qaestudos.painel.triagem.ia.IaIndisponivelException("Gemini respondeu 503: sobrecarregado"));

        assertThat(mvc.post().uri("/api/triagem/{id}/analisar", resultadoQueFalha))
                .hasStatus(502)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("sobrecarregado");
    }

    @Test
    void classificacaoInexistenteDevolve400() {
        assertThat(mvc.put().uri("/api/triagem/{id}", resultadoQueFalha).contentType(MediaType.APPLICATION_JSON)
                .content("{\"classificacao\":\"CULPA_DO_DEV\"}")).hasStatus(400);
    }
}
