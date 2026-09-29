package com.qaestudos.painel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Teste de INTEGRAÇÃO: sobe a aplicação inteira com o application.yml real
 * (sem mocks) e passa por todas as camadas — Controller → Service →
 * Repository → configuração.
 *
 * <p>Substitui o "contextLoads" vazio gerado pelo Initializr, que subia o
 * contexto sem verificar nada e deixou passar um erro de configuração.
 */
@SpringBootTest(properties = "painel.seguranca.chave-mestra=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PainelBackendApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiListaOsTresProjetosConfigurados() {
        assertThat(mvc.get().uri("/api/projetos"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly("cypress", "playwright", "k6");
    }

    @Test
    void historicoDeProjetoSemExecucoesVemVazio() {
        assertThat(mvc.get().uri("/api/execucoes?projeto=k6"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[]");
    }

    @Test
    void historicoDeProjetoInexistenteDevolve404() {
        assertThat(mvc.get().uri("/api/execucoes?projeto=selenium")).hasStatus(404);
    }

    @Test
    void healthCheckEstaNoAr() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }
}
