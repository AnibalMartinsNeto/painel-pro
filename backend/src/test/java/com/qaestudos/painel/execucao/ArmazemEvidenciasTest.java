package com.qaestudos.painel.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazemEvidenciasTest {

    @TempDir
    Path tmp;

    @Test
    void guardaUmaCopiaPorExecucaoQueSobreviveAoOriginalSerApagado() throws IOException {
        Path original = Files.writeString(tmp.resolve("test-failed-1.png"), "png");
        var armazem = new ArmazemEvidencias(tmp.resolve("evidencias").toString());

        Evidencia e = armazem.guardar(42L, 7L, original).orElseThrow();
        Files.delete(original); // a próxima execução sobrescreve/apaga o original

        assertThat(e.getArquivo()).isEqualTo("42/7-test-failed-1.png");
        assertThat(e.getTipo()).isEqualTo("image/png");
        assertThat(e.getTamanho()).isEqualTo(3);
        assertThat(armazem.abrir(e.getArquivo())).hasValueSatisfying(p -> assertThat(p).hasContent("png"));
    }

    @Test
    void arquivoQueNaoExisteNaoViraEvidenciaECaminhoParaForaDaPastaEhRecusado() throws IOException {
        var armazem = new ArmazemEvidencias(tmp.resolve("evidencias").toString());
        Files.writeString(tmp.resolve("segredo.txt"), "x");

        assertThat(armazem.guardar(1L, 1L, tmp.resolve("nao-existe.png"))).isEmpty();
        assertThat(armazem.abrir("../segredo.txt")).isEmpty();
    }
}
