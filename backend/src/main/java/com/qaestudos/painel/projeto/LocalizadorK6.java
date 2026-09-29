package com.qaestudos.painel.projeto;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Encontra o executável do k6 (não é pacote npm): variável K6_BIN, pasta
 * padrão do winget ou PATH. Usado para saber se o k6 está instalado e
 * para executá-lo.
 */
public final class LocalizadorK6 {

    private LocalizadorK6() {}

    public static Optional<Path> localizar() {
        String programFiles = Objects.requireNonNullElse(System.getenv("ProgramFiles"), "C:\\Program Files");
        Stream<Path> candidatos = Stream.concat(
                Stream.of(System.getenv("K6_BIN")).filter(Objects::nonNull).map(Path::of),
                Stream.concat(
                        Stream.of(Path.of(programFiles, "k6", "k6.exe")),
                        Arrays.stream(Objects.requireNonNullElse(System.getenv("PATH"), "").split(File.pathSeparator))
                                .filter(s -> !s.isBlank())
                                .flatMap(dir -> Stream.of(Path.of(dir, "k6.exe"), Path.of(dir, "k6")))));
        return candidatos.filter(Files::isRegularFile).findFirst();
    }
}
