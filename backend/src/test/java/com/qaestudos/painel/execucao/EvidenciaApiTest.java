package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.TestcontainersConfiguration;
import com.qaestudos.painel.execucao.executor.SolicitacaoExecucao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Evidência de ponta a ponta: o executor anexa o screenshot ao resultado,
 * a gravação copia para a pasta de evidências, o detalhe da execução lista
 * a URL e a API devolve a imagem — mesmo depois de o original sumir.
 */
@SpringBootTest(properties = "painel.seguranca.chave-mestra=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenciaApiTest {

    @TempDir
    static Path tmp;

    @DynamicPropertySource
    static void pastaDeEvidencias(DynamicPropertyRegistry r) {
        r.add("painel.evidencias.pasta", () -> tmp.resolve("evidencias").toString());
    }

    @Autowired MockMvcTester mvc;
    @Autowired ExecucaoGravacao gravacao;

    @Test
    void screenshotDaFalhaApareceNoDetalheEEhServidoPelaApi() throws IOException {
        Path screenshot = Files.write(tmp.resolve("test-failed-1.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Execucao e = gravacao.criar(new SolicitacaoExecucao("playwright", null, List.of("tests/a.spec.js"), "chromium", 0, false));
        ResultadoTeste falha = new ResultadoTeste("tests/a.spec.js", "A › quebra", StatusTeste.FALHOU, 10L, "erro", "Asserção");
        falha.anexar(screenshot);
        gravacao.concluir(e.getId(), StatusExecucao.FALHOU, List.of(falha), "Playwright", null, "log");
        Files.delete(screenshot); // a próxima execução apagaria o original

        var detalhe = mvc.get().uri("/api/execucoes/{id}", e.getId()).exchange();
        assertThat(detalhe).hasStatusOk().bodyJson().extractingPath("$.resultados[0].evidencias[0].tipo").isEqualTo("image/png");
        String url = com.jayway.jsonpath.JsonPath.read(detalhe.getResponse().getContentAsString(), "$.resultados[0].evidencias[0].url");

        assertThat(mvc.get().uri(url)).hasStatusOk().hasContentType(MediaType.IMAGE_PNG)
                .hasHeader("Content-Disposition", "inline; filename=\"test-failed-1.png\"")
                .body().isEqualTo(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        assertThat(mvc.get().uri("/api/evidencias/999999")).hasStatus(404);
    }
}
