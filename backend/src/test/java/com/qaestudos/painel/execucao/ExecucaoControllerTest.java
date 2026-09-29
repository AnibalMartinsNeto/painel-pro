package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qaestudos.painel.common.ConflitoException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Contrato HTTP de disparo de execuções: status 202/400/409/204 e formato dos erros. */
@WebMvcTest(ExecucaoController.class)
class ExecucaoControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ExecucaoService service;

    @MockitoBean
    OrquestradorExecucao orquestrador;

    private MockMvcTester.MockMvcRequestBuilder post(String corpo) {
        return mvc.post().uri("/api/execucoes").contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void iniciarDevolve202ComAExecucao() {
        given(orquestrador.iniciar(any())).willReturn(new Execucao("cypress", "test", "electron", Instant.parse("2026-09-29T10:00:00Z")));

        assertThat(post("""
                {"projeto":"cypress","script":"test","specs":["cypress/e2e/login.cy.js"],"navegador":"electron"}
                """))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"projetoId":"cypress","script":"test","status":"EM_ANDAMENTO"}
                        """);
    }

    @Test
    void corpoInvalidoDevolve400ListandoOsCampos() {
        assertThat(post("""
                {"projeto":"","specs":[],"retentativas":9}
                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("projeto:", "specs: selecione ao menos um spec", "retentativas:");
    }

    @Test
    void jsonMalformadoDevolve400NoFormatoPadrao() {
        assertThat(post("{ isso não é json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("JSON inválido");
    }

    @Test
    void execucaoJaEmAndamentoDevolve409() {
        given(orquestrador.iniciar(any())).willThrow(new ConflitoException("Já existe uma execução em andamento."));

        assertThat(post("""
                {"projeto":"cypress","specs":["a.cy.js"]}
                """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Conflito");
    }

    @Test
    void semExecucaoEmAndamentoDevolve204() {
        given(orquestrador.emAndamento()).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/execucoes/em-andamento")).hasStatus(HttpStatus.NO_CONTENT);
    }
}
