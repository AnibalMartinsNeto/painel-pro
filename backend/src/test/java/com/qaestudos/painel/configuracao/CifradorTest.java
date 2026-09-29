package com.qaestudos.painel.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CifradorTest {

    private static byte[] chave(int preenchimento) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) preenchimento);
        return k;
    }

    private final Cifrador cifrador = new Cifrador(chave(7));

    @Test
    void cifraEDecifraDeVolta() {
        String cifrado = cifrador.cifrar("AQ.minha-chave-secreta");

        assertThat(cifrado).startsWith("v1:").doesNotContain("minha-chave-secreta");
        assertThat(cifrador.decifrar(cifrado)).isEqualTo("AQ.minha-chave-secreta");
    }

    @Test
    void mesmoSegredoGeraTextosDiferentes() {
        // IV aleatório: quem olha o banco não descobre que dois valores são iguais.
        assertThat(cifrador.cifrar("igual")).isNotEqualTo(cifrador.cifrar("igual"));
    }

    @Test
    void valorAdulteradoNoBancoEDetectado() {
        String cifrado = cifrador.cifrar("segredo");
        byte[] bytes = Base64.getDecoder().decode(cifrado.substring(3));
        bytes[bytes.length - 1] ^= 1; // altera um único bit
        String adulterado = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cifrador.decifrar(adulterado)).hasMessageContaining("alterado");
    }

    @Test
    void chaveMestraErradaNaoAbreOSegredo() {
        String cifrado = cifrador.cifrar("segredo");
        assertThatThrownBy(() -> new Cifrador(chave(9)).decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void criaAChaveMestraNoArquivoNaPrimeiraVezEReaproveitaDepois(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("sub/chave-mestra.key");

        String cifrado = new Cifrador("", arquivo.toString()).cifrar("segredo");

        assertThat(Base64.getDecoder().decode(Files.readString(arquivo))).hasSize(32);
        assertThat(new Cifrador("", arquivo.toString()).decifrar(cifrado)).isEqualTo("segredo");
    }

    @Test
    void recusaChaveMestraDoTamanhoErrado() {
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new Cifrador(curta, "nao-usado")).hasMessageContaining("32 bytes");
    }
}
