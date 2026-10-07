package com.qaestudos.painel.execucao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pasta de evidências do painel: onde ficam as CÓPIAS dos screenshots e
 * traces. Os originais vivem dentro do projeto de testes e a próxima
 * execução os sobrescreve; aqui eles ficam por execução:
 * {@code <pasta>/<execucaoId>/<resultadoId>-<nome>}.
 *
 * <p>Padrão: {@code ~/.qapanel/evidencias} (fora do Git e do projeto).
 * Configurável em {@code painel.evidencias.pasta}.
 */
@Component
public class ArmazemEvidencias {

    private static final Logger log = LoggerFactory.getLogger(ArmazemEvidencias.class);
    private static final long TAMANHO_MAXIMO = 50L * 1024 * 1024; // um trace gigante não enche o disco

    private final Path raiz;

    public ArmazemEvidencias(@Value("${painel.evidencias.pasta:}") String pasta) {
        this.raiz = (pasta == null || pasta.isBlank()
                ? Path.of(System.getProperty("user.home"), ".qapanel", "evidencias")
                : Path.of(pasta)).toAbsolutePath().normalize();
    }

    /** Copia o arquivo para a pasta da execução. Vazio se ele não existe ou é grande demais. */
    public Optional<Evidencia> guardar(Long execucaoId, Long resultadoId, Path origem) {
        try {
            if (origem == null || !Files.isRegularFile(origem)) return Optional.empty();
            long tamanho = Files.size(origem);
            if (tamanho > TAMANHO_MAXIMO) {
                log.warn("Evidência {} ignorada: {} bytes (máximo {}).", origem, tamanho, TAMANHO_MAXIMO);
                return Optional.empty();
            }
            String nome = origem.getFileName().toString();
            String relativo = execucaoId + "/" + resultadoId + "-" + nome.replaceAll("[^\\w.\\- ()]", "_");
            Path destino = raiz.resolve(relativo);
            Files.createDirectories(destino.getParent());
            Files.copy(origem, destino, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(new Evidencia(nome, tipo(nome), relativo, tamanho));
        } catch (IOException e) {
            // Evidência é um bônus: a execução é gravada mesmo se a cópia falhar.
            log.warn("Não foi possível guardar a evidência {}: {}", origem, e.getMessage());
            return Optional.empty();
        }
    }

    /** O arquivo de uma evidência. Recusa caminho que sai da pasta (ex.: "../../segredo"). */
    public Optional<Path> abrir(String arquivoRelativo) {
        Path p = raiz.resolve(arquivoRelativo).normalize();
        return p.startsWith(raiz) && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    static String tipo(String nome) {
        String n = nome.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".md")) return "text/markdown; charset=UTF-8";
        if (n.endsWith(".txt") || n.endsWith(".log")) return "text/plain; charset=UTF-8";
        return "application/octet-stream";
    }
}
