package com.qaestudos.painel.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.triagem.ia.IaIndisponivelException;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssistenteTriagemTest {

    private final ContextoFalha falha = new ContextoFalha("Cypress", "cypress/e2e/login.cy.js", "Login › bloqueado",
            "AssertionError: expected 'x' to contain 'locked'\n    at Context...", "Asserção", "electron", "describe('Login', ...)");

    private ConfiguracaoService config;
    private String promptRecebido;
    private String respostaDaIa;
    private AssistenteTriagem assistente;

    @BeforeEach
    void setUp() {
        config = mock(ConfiguracaoService.class);
        when(config.valor(ChaveConfig.IA_PROVEDOR)).thenReturn(Optional.of("gemini"));
        when(config.valor(ChaveConfig.IA_GEMINI_MODELO)).thenReturn(Optional.of("gemini-teste"));
        when(config.valor(ChaveConfig.IA_GEMINI_CHAVE)).thenReturn(Optional.of("chave"));
        ProvedorIa iaFalsa = new ProvedorIa() {
            public String id() { return "gemini"; }
            public Resposta gerar(String prompt, String modelo, String chave) {
                promptRecebido = prompt;
                return new Resposta(respostaDaIa, modelo);
            }
        };
        assistente = new AssistenteTriagem(config, List.of(iaFalsa), JsonMapper.builder().build());
    }

    @Test
    void comChaveMandaErroECodigoParaAIaEInterpretaOJson() {
        respostaDaIa = """
                {"titulo":"Login bloqueado sem mensagem","classificacao":"BUG_APLICACAO","severidade":"ALTA",
                 "esperado":"Mensagem de bloqueio","encontrado":"Nenhuma mensagem","passos":["Abrir login","Entrar com locked_out_user"],
                 "analise":"Parece defeito do app."}
                """;

        RascunhoBug r = assistente.gerarRascunho(falha);

        assertThat(promptRecebido).contains("AssertionError", "describe('Login'", "Cypress", "Login › bloqueado");
        assertThat(r.titulo()).isEqualTo("Login bloqueado sem mensagem");
        assertThat(r.classificacao()).isEqualTo(Classificacao.BUG_APLICACAO);
        assertThat(r.severidade()).isEqualTo(Severidade.ALTA);
        assertThat(r.passos()).containsExactly("Abrir login", "Entrar com locked_out_user");
        assertThat(r.origem()).isEqualTo("IA");
        assertThat(r.modelo()).isEqualTo("gemini-teste");
    }

    @Test
    void toleraMarkdownEmVoltaEValoresForaDaLista() {
        respostaDaIa = "Claro! Aqui está:\n```json\n{\"titulo\":\"T\",\"classificacao\":\"TALVEZ\",\"severidade\":\"media\"}\n```";

        RascunhoBug r = assistente.gerarRascunho(falha);

        assertThat(r.titulo()).isEqualTo("T");
        assertThat(r.classificacao()).isNull(); // "TALVEZ" não existe: o QA escolhe na tela
        assertThat(r.severidade()).isEqualTo(Severidade.MEDIA); // aceita minúsculas
    }

    @Test
    void respostaSemJsonViraErroDeIa() {
        respostaDaIa = "Desculpe, não consigo ajudar.";
        assertThatThrownBy(() -> assistente.gerarRascunho(falha)).isInstanceOf(IaIndisponivelException.class);
    }

    @Test
    void semChaveUsaAHeuristicaSemChamarAIa() {
        when(config.valor(ChaveConfig.IA_GEMINI_CHAVE)).thenReturn(Optional.empty());

        RascunhoBug r = assistente.gerarRascunho(falha);

        assertThat(promptRecebido).isNull(); // a IA nem foi chamada
        assertThat(r.origem()).isEqualTo("HEURISTICA");
        assertThat(r.titulo()).isEqualTo("[login] bloqueado falhou");
        assertThat(r.encontrado()).isEqualTo("AssertionError: expected 'x' to contain 'locked'");
        assertThat(r.classificacao()).isEqualTo(Classificacao.BUG_APLICACAO);
        assertThat(r.passos()).containsExactly("Acessar o fluxo: Login", "Executar: bloqueado", "Observar o resultado");
    }
}
