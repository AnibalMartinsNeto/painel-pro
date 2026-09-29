package com.qaestudos.painel.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Teste de ponta a ponta da API de configurações (HTTP → serviço →
 * criptografia → PostgreSQL). A chave-mestra vem de uma propriedade fixa,
 * para o teste não criar arquivo na pasta do usuário.
 */
@SpringBootTest(properties = "painel.seguranca.chave-mestra=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConfiguracaoApiTest {

    private static final String SEGREDO = "AQ.nao-pode-vazar-12345";

    @Autowired
    MockMvcTester mvc;

    @Test
    void segredoEntraPeloPutMasNuncaSaiPeloGet() {
        var put = mvc.put().uri("/api/configuracoes").contentType(MediaType.APPLICATION_JSON).content("""
                {"ambiente":"QA","ia":{"provedor":"gemini","chaveGemini":"%s"},
                 "azure":{"organizacao":"org","projeto":"proj","pat":"pat-secreto"}}
                """.formatted(SEGREDO));

        assertThat(put).hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"ambiente":"QA",
                         "ia":{"provedor":"gemini","geminiConfigurada":true,"ativa":true},
                         "azure":{"organizacao":"org","projeto":"proj","patConfigurado":true,"configurado":true}}
                        """);
        assertThat(put).body().asString().doesNotContain(SEGREDO).doesNotContain("pat-secreto");

        var get = mvc.get().uri("/api/configuracoes");
        assertThat(get).hasStatusOk();
        assertThat(get).body().asString().doesNotContain(SEGREDO).doesNotContain("pat-secreto");
    }

    @Test
    void provedorInvalidoDevolve400EMantemOEstadoAnterior() {
        assertThat(mvc.put().uri("/api/configuracoes").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ia":{"provedor":"chatgpt"}}
                        """))
                .hasStatus(400);
    }
}
