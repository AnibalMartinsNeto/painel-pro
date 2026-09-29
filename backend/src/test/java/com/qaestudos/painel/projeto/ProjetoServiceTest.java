package com.qaestudos.painel.projeto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste de UNIDADE do Service: sem Spring, sem servidor, sem banco.
 *
 * <p>O repositório é substituído por uma implementação falsa em memória e
 * as pastas de projeto são criadas numa pasta temporária ({@code @TempDir})
 * que o JUnit apaga no fim. Cada teste roda em milissegundos e não depende
 * da máquina de quem executa.
 */
class ProjetoServiceTest {

    @TempDir
    Path tmp;

    private Projeto cypress;
    private ProjetoService service;

    @BeforeEach
    void setUp() {
        cypress = new Projeto("cypress", "Cypress", TipoProjeto.CYPRESS, tmp.resolve("Cypress"),
                "cypress/e2e", Pattern.compile(".*\\.cy\\.js$"), List.of("electron"));
        ProjetoRepository repositorioFalso = new ProjetoRepository() {
            @Override
            public List<Projeto> findAll() {
                return List.of(cypress);
            }

            @Override
            public Optional<Projeto> findById(String id) {
                return findAll().stream().filter(p -> p.id().equals(id)).findFirst();
            }
        };
        service = new ProjetoService(repositorioFalso, JsonMapper.builder().build());
    }

    @Test
    void listarScriptsLeOPackageJsonEIgnoraScriptsQueNaoRodamTestes() throws IOException {
        Files.createDirectories(cypress.diretorio());
        Files.writeString(cypress.diretorio().resolve("package.json"), """
                {"name":"x","scripts":{
                  "cy:open":"cypress open",
                  "test":"cypress run --spec \\"cypress/e2e/login.cy.js\\"",
                  "test:all":"cypress run"}}
                """);

        List<ScriptExecucao> scripts = service.listarScripts(cypress, List.of("cypress/e2e/login.cy.js", "cypress/e2e/checkout.cy.js"));

        assertThat(scripts).extracting(ScriptExecucao::nome).containsExactly("test", "test:all");
        assertThat(scripts.get(0).specs()).containsExactly("cypress/e2e/login.cy.js");
        assertThat(scripts.get(1).specs()).hasSize(2); // sem --spec: roda todos
    }

    @Test
    void listarScriptsDevolveVazioComPackageJsonInvalido() throws IOException {
        Files.createDirectories(cypress.diretorio());
        Files.writeString(cypress.diretorio().resolve("package.json"), "{ isso não é json");
        assertThat(service.listarScripts(cypress, List.of())).isEmpty();
    }

    @Test
    void baseUrlLeODeclaradoNoArquivoDeConfiguracao() throws IOException {
        Files.createDirectories(cypress.diretorio());
        Files.writeString(cypress.diretorio().resolve("cypress.config.js"), "e2e: { baseUrl: \"https://minha.app\" }");
        assertThat(service.baseUrl(cypress)).contains("https://minha.app");
    }

    @Test
    void buscarDevolveOProjetoQuandoOIdExiste() {
        assertThat(service.buscar("cypress").nome()).isEqualTo("Cypress");
    }

    @Test
    void buscarLancaNaoEncontradoQuandoOIdNaoExiste() {
        assertThatThrownBy(() -> service.buscar("selenium"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("selenium");
    }

    @Test
    void statusIndicaPastaAusente() {
        assertThat(service.status(cypress)).isEqualTo(new StatusProjeto(false, false));
    }

    @Test
    void statusIndicaEncontradoMasSemDependencias() throws IOException {
        Files.createDirectories(cypress.diretorio());
        assertThat(service.status(cypress)).isEqualTo(new StatusProjeto(true, false));
    }

    @Test
    void statusIndicaInstaladoQuandoONodeModulesTemAFerramenta() throws IOException {
        Files.createDirectories(cypress.diretorio().resolve("node_modules/cypress"));
        assertThat(service.status(cypress)).isEqualTo(new StatusProjeto(true, true));
    }

    @Test
    void listarSpecsFiltraPeloPadraoEOrdena() throws IOException {
        Path e2e = Files.createDirectories(cypress.diretorio().resolve("cypress/e2e/sub"));
        Files.createFile(e2e.getParent().resolve("login.cy.js"));
        Files.createFile(e2e.resolve("checkout.cy.js"));
        Files.createFile(e2e.getParent().resolve("helpers.js")); // não é spec

        assertThat(service.listarSpecs(cypress))
                .containsExactly("cypress/e2e/login.cy.js", "cypress/e2e/sub/checkout.cy.js");
    }

    @Test
    void listarSpecsDevolveVazioQuandoAPastaDeSpecsNaoExiste() {
        assertThat(service.listarSpecs(cypress)).isEmpty();
    }
}
