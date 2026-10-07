package com.qaestudos.painel.projeto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentoServiceTest {

    @TempDir
    Path tmp;

    private DocumentoService service;

    @BeforeEach
    void setUp() throws IOException {
        Path repo = Files.createDirectories(tmp.resolve("serverest-qa"));
        Path pw = Files.createDirectories(repo.resolve("playwright"));
        Files.writeString(repo.resolve("README.md"), "# ServeRest QA\ntexto");
        Files.writeString(repo.resolve("REGRAS_DE_NEGOCIO.md"), "# Regras de negócio – ServeRest\n- USU-03");
        Files.writeString(pw.resolve("README.md"), "sem título\n");
        Files.createDirectories(pw.resolve("node_modules/x"));
        Files.writeString(pw.resolve("node_modules/x/README.md"), "# não aparece (subpasta)");
        Files.writeString(tmp.resolve("fora.md"), "# fora do projeto");

        ProjetoService projetos = mock(ProjetoService.class);
        when(projetos.buscar("playwright")).thenReturn(
                new Projeto("playwright", "Playwright", TipoProjeto.PLAYWRIGHT, pw, "tests", Pattern.compile(".*"), List.of()));
        service = new DocumentoService(projetos);
    }

    @Test
    void listaOsMdDaPastaDoProjetoEDaRaizDoRepositorioComReadmePrimeiro() {
        assertThat(service.listar("playwright")).extracting(DocumentoService.Documento::id, DocumentoService.Documento::titulo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("serverest-qa/README.md", "ServeRest QA"),
                        org.assertj.core.groups.Tuple.tuple("serverest-qa/REGRAS_DE_NEGOCIO.md", "Regras de negócio – ServeRest"),
                        org.assertj.core.groups.Tuple.tuple("playwright/README.md", "README.md")); // sem "# ": nome do arquivo
    }

    @Test
    void soLeDocumentoDescobertoNuncaUmCaminhoQualquer() {
        assertThat(service.ler("playwright", "serverest-qa/REGRAS_DE_NEGOCIO.md")).contains("USU-03");
        assertThatThrownBy(() -> service.ler("playwright", "../fora.md")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.ler("playwright", "playwright/node_modules/x/README.md"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
