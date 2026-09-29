package com.qaestudos.painel.projeto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Teste da camada WEB: sobe só o necessário para o Controller (rotas,
 * conversão JSON, tratamento de erros) e troca o Service por um mock.
 *
 * <p>Valida o CONTRATO da API — URL, status HTTP e formato do JSON — sem
 * depender da regra de negócio, que já é coberta pelo ProjetoServiceTest.
 */
@WebMvcTest(ProjetoController.class)
class ProjetoControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ProjetoService service;

    private final Projeto playwright = new Projeto("playwright", "Playwright", TipoProjeto.PLAYWRIGHT,
            Path.of("/qualquer"), "tests", Pattern.compile(".*"), List.of("chromium", "firefox"));

    @Test
    void listarDevolveOsProjetosComStatus() {
        given(service.listar()).willReturn(List.of(playwright));
        given(service.status(playwright)).willReturn(new StatusProjeto(true, false));

        assertThat(mvc.get().uri("/api/projetos"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id":"playwright","nome":"Playwright","tipo":"PLAYWRIGHT","encontrado":true,"instalado":false}]
                        """);
    }

    @Test
    void detalharDevolveSpecsENavegadores() {
        given(service.buscar("playwright")).willReturn(playwright);
        given(service.status(playwright)).willReturn(new StatusProjeto(true, true));
        given(service.listarSpecs(playwright)).willReturn(List.of("tests/login.spec.js"));

        assertThat(mvc.get().uri("/api/projetos/playwright"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id":"playwright","navegadores":["chromium","firefox"],"specs":["tests/login.spec.js"]}
                        """);
    }

    @Test
    void detalharDevolve404NoFormatoProblemDetail() {
        given(service.buscar("selenium")).willThrow(new RecursoNaoEncontradoException("Projeto 'selenium' não existe."));

        assertThat(mvc.get().uri("/api/projetos/selenium"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status":404,"title":"Recurso não encontrado","detail":"Projeto 'selenium' não existe."}
                        """);
    }

    @Test
    void naoExpoeODiretorioInternoNaResposta() {
        given(service.listar()).willReturn(List.of(playwright));
        given(service.status(playwright)).willReturn(new StatusProjeto(true, true));

        assertThat(mvc.get().uri("/api/projetos"))
                .bodyJson()
                .doesNotHavePath("$[0].diretorio");
    }
}
