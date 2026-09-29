package com.qaestudos.painel.configuracao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Criptografa e decifra segredos com AES-256-GCM.
 *
 * <ul>
 *   <li><b>AES-256</b>: cifra simétrica padrão da indústria, chave de 256 bits.
 *   <li><b>GCM</b>: além de esconder o conteúdo, AUTENTICA — se alguém alterar
 *       um byte do valor no banco, a decifragem falha em vez de devolver lixo.
 *   <li><b>IV aleatório</b> a cada cifragem: o mesmo segredo gravado duas vezes
 *       gera textos diferentes, então não dá para descobrir valores iguais.
 * </ul>
 *
 * <p>A CHAVE-MESTRA vem, nesta ordem: da propriedade
 * {@code painel.seguranca.chave-mestra} (ex.: variável de ambiente
 * PAINEL_SEGURANCA_CHAVE_MESTRA, o jeito de produção) ou de um arquivo em
 * {@code ~/.qapanel/chave-mestra.key}, criado na primeira execução (o jeito
 * de desenvolvimento). Ela nunca vai para o banco nem para o Git.
 */
@Component
public class Cifrador {

    private static final Logger log = LoggerFactory.getLogger(Cifrador.class);
    private static final String PREFIXO = "v1:"; // versão do formato: permite trocar o algoritmo no futuro
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey chave;
    private final SecureRandom aleatorio = new SecureRandom();

    // Com mais de um construtor, o Spring precisa saber qual usar: @Autowired indica.
    @Autowired
    public Cifrador(
            @Value("${painel.seguranca.chave-mestra:}") String chaveBase64,
            @Value("${painel.seguranca.arquivo-chave:${user.home}/.qapanel/chave-mestra.key}") String arquivoChave) {
        this.chave = new SecretKeySpec(carregarChave(chaveBase64, Path.of(arquivoChave)), "AES");
    }

    /** Construtor direto (testes): chave de 32 bytes. */
    Cifrador(byte[] chave) {
        this.chave = new SecretKeySpec(chave, "AES");
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            // Guarda IV + texto cifrado juntos: o IV não é secreto, só precisa ser único.
            return PREFIXO + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar", e);
        }
    }

    public String decifrar(String armazenado) {
        if (!armazenado.startsWith(PREFIXO)) {
            throw new IllegalArgumentException("Formato de segredo desconhecido.");
        }
        try {
            ByteBuffer dados = ByteBuffer.wrap(Base64.getDecoder().decode(armazenado.substring(PREFIXO.length())));
            byte[] iv = new byte[IV_BYTES];
            dados.get(iv);
            byte[] cifrado = new byte[dados.remaining()];
            dados.get(cifrado);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Chave-mestra diferente da usada para cifrar, ou valor adulterado.
            throw new IllegalStateException("Não foi possível decifrar o segredo (chave-mestra errada ou valor alterado).", e);
        }
    }

    private static byte[] carregarChave(String base64, Path arquivo) {
        if (!base64.isBlank()) {
            return validar(Base64.getDecoder().decode(base64.trim()));
        }
        try {
            if (Files.isRegularFile(arquivo)) {
                return validar(Base64.getDecoder().decode(Files.readString(arquivo).trim()));
            }
            byte[] nova = new byte[32];
            new SecureRandom().nextBytes(nova);
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, Base64.getEncoder().encodeToString(nova));
            restringirPermissoes(arquivo);
            log.warn("Chave-mestra criada em {}. Faça backup: sem ela, os segredos salvos não podem ser lidos.", arquivo);
            return nova;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler/criar a chave-mestra em " + arquivo, e);
        }
    }

    private static byte[] validar(byte[] chave) {
        if (chave.length != 32) {
            throw new IllegalStateException("A chave-mestra deve ter 32 bytes (256 bits) em Base64; tem " + chave.length + ".");
        }
        return chave;
    }

    private static void restringirPermissoes(Path arquivo) {
        try {
            Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows não tem permissões POSIX; a pasta do usuário já é restrita a ele.
        }
    }
}
