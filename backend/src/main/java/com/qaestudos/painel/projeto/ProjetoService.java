package com.qaestudos.painel.projeto;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio sobre projetos: localizar, verificar se estão prontos
 * para rodar e listar os arquivos de teste (specs).
 *
 * <p>Não conhece HTTP nem JSON — isso é papel do Controller. Por isso dá
 * para testar esta classe sem subir servidor nenhum (ver ProjetoServiceTest).
 */
@Service
public class ProjetoService {

    private final ProjetoRepository repository;

    public ProjetoService(ProjetoRepository repository) {
        this.repository = repository;
    }

    public List<Projeto> listar() {
        return repository.findAll();
    }

    public Projeto buscar(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto '%s' não existe.".formatted(id)));
    }

    public StatusProjeto status(Projeto projeto) {
        boolean encontrado = Files.isDirectory(projeto.diretorio());
        boolean instalado = encontrado && switch (projeto.tipo()) {
            case CYPRESS -> Files.isDirectory(projeto.diretorio().resolve("node_modules/cypress"));
            case PLAYWRIGHT -> Files.isDirectory(projeto.diretorio().resolve("node_modules/@playwright/test"));
            case K6 -> k6Instalado();
        };
        return new StatusProjeto(encontrado, instalado);
    }

    /** Specs do projeto, com caminho relativo à pasta do projeto e barras "/". */
    public List<String> listarSpecs(Projeto projeto) {
        Path raiz = projeto.diretorio().resolve(projeto.pastaSpecs());
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            return arquivos
                    .filter(Files::isRegularFile)
                    .filter(p -> projeto.padraoSpec().matcher(p.getFileName().toString()).matches())
                    .map(p -> projeto.diretorio().relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar specs de " + projeto.id(), e);
        }
    }

    // O k6 é um executável, não um pacote npm: procura no PATH e na pasta
    // padrão de instalação do winget.
    private boolean k6Instalado() {
        String programFiles = Objects.requireNonNullElse(System.getenv("ProgramFiles"), "C:\\Program Files");
        Stream<Path> candidatos = Stream.concat(
                Stream.of(Path.of(programFiles, "k6", "k6.exe")),
                Arrays.stream(Objects.requireNonNullElse(System.getenv("PATH"), "").split(java.io.File.pathSeparator))
                        .filter(s -> !s.isBlank())
                        .flatMap(dir -> Stream.of(Path.of(dir, "k6.exe"), Path.of(dir, "k6"))));
        return candidatos.anyMatch(Files::isRegularFile);
    }
}
