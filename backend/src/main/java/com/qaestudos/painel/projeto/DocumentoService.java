package com.qaestudos.painel.projeto;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Documentação do projeto de testes dentro do painel: os {@code .md} da
 * pasta do projeto (ex.: README do Playwright) e da pasta acima dela (ex.:
 * README e REGRAS_DE_NEGOCIO do serverest-qa).
 *
 * <p>Só arquivos DESCOBERTOS aqui podem ser lidos (pelo id): a API nunca
 * recebe um caminho, então não dá para pedir "../../outra-coisa.md".
 */
@Service
public class DocumentoService {

    private final ProjetoService projetos;

    public DocumentoService(ProjetoService projetos) {
        this.projetos = projetos;
    }

    /**
     * @param id     identificador estável na URL ("serverest-qa/REGRAS_DE_NEGOCIO.md")
     * @param titulo o primeiro "# título" do arquivo, ou o nome dele
     */
    public record Documento(String id, String titulo, String pasta) {}

    public List<Documento> listar(String projetoId) {
        Projeto projeto = projetos.buscar(projetoId);
        List<Documento> docs = new ArrayList<>();
        for (Path pasta : pastas(projeto)) {
            for (Path md : markdowns(pasta)) {
                docs.add(new Documento(id(pasta, md), titulo(md), pasta.getFileName().toString()));
            }
        }
        return docs;
    }

    public String ler(String projetoId, String id) {
        Projeto projeto = projetos.buscar(projetoId);
        for (Path pasta : pastas(projeto)) {
            for (Path md : markdowns(pasta)) {
                if (id(pasta, md).equals(id)) {
                    try {
                        return Files.readString(md);
                    } catch (IOException e) {
                        break;
                    }
                }
            }
        }
        throw new RecursoNaoEncontradoException("Documento '%s' não existe no projeto %s.".formatted(id, projetoId));
    }

    /** A pasta do projeto e a de cima (a raiz do repositório de testes), se existirem. */
    private static List<Path> pastas(Projeto projeto) {
        Path dir = projeto.diretorio();
        List<Path> pastas = new ArrayList<>();
        if (dir.getParent() != null && Files.isDirectory(dir.getParent())) pastas.add(dir.getParent());
        if (Files.isDirectory(dir)) pastas.add(dir);
        return pastas;
    }

    /** Os .md direto na pasta (sem descer em subpastas), README primeiro. */
    private static List<Path> markdowns(Path pasta) {
        try (Stream<Path> s = Files.list(pasta)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted((a, b) -> {
                        boolean ra = a.getFileName().toString().equalsIgnoreCase("README.md");
                        boolean rb = b.getFileName().toString().equalsIgnoreCase("README.md");
                        return ra == rb ? a.getFileName().compareTo(b.getFileName()) : (ra ? -1 : 1);
                    })
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String id(Path pasta, Path md) {
        return pasta.getFileName() + "/" + md.getFileName();
    }

    private static String titulo(Path md) {
        try (Stream<String> linhas = Files.lines(md)) {
            return linhas.filter(l -> l.startsWith("# ")).findFirst().map(l -> l.substring(2).strip())
                    .orElse(md.getFileName().toString());
        } catch (IOException | RuntimeException e) {
            return md.getFileName().toString();
        }
    }
}
