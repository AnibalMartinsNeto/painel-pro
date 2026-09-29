package com.qaestudos.painel.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.TestcontainersConfiguration;
import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ExecucaoRepository;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.execucao.StatusTeste;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/** Importação do histórico do painel Node, contra um PostgreSQL real. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ImportadorPainelNodeTest {

    @Autowired
    ExecucaoRepository repository;

    @TempDir
    Path pasta;

    private ImportadorPainelNode importador;

    // Amostra no formato gravado pelo painel Node (campos reduzidos).
    private static final String RUN = """
            {"id":"2026-09-28T23-50-58-517Z","startedAt":"2026-09-28T23:50:58.517Z","finishedAt":"2026-09-28T23:51:40.000Z",
             "status":"failed","browser":"electron 138","cypressVersion":"15.19.0",
             "options":{"project":"cypress","preset":"test:diagnostics","browser":"electron"},
             "stats":{"duration":37036},
             "specs":[{"spec":"cypress/e2e/user-behavior-matrix.cy.js","tests":[
               {"title":["Matriz","Usuário: standard_user","login"],"state":"passed","duration":2000},
               {"title":["Matriz","Usuário: problem_user","imagens"],"state":"failed","duration":66.4,
                "error":"AssertionError: imagens duplicadas","errorType":"Asserção"}]}]}
            """;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(pasta.resolve("2026-09-28T23-50-58-517Z.json"), RUN);
        Files.writeString(pasta.resolve("2026-09-28T23-50-58-517Z.log"), "log, deve ser ignorado");
        importador = new ImportadorPainelNode(repository, JsonMapper.builder().build(), pasta.toString());
    }

    @Test
    void importaExecucaoComResultadosEStatusConvertidos() {
        var resultado = importador.importar();

        assertThat(resultado.importadas()).isEqualTo(1);
        assertThat(resultado.erros()).isEmpty();
        Execucao e = repository.findAll().getFirst();
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.FALHOU);
        assertThat(e.getScript()).isEqualTo("test:diagnostics");
        assertThat(e.getVersaoFerramenta()).isEqualTo("Cypress 15.19.0");
        assertThat(e.getTotal()).isEqualTo(2);
        assertThat(e.getReprovados()).isEqualTo(1);
        assertThat(e.getResultados()).extracting(r -> r.getStatus()).containsExactly(StatusTeste.PASSOU, StatusTeste.FALHOU);
        assertThat(e.getResultados().get(1).getTitulo()).isEqualTo("Matriz › Usuário: problem_user › imagens");
        assertThat(e.getResultados().get(1).getDuracaoMs()).isEqualTo(66L); // decimal arredondado
    }

    @Test
    void importarDuasVezesNaoDuplica() {
        importador.importar();
        var segunda = importador.importar();

        assertThat(segunda.importadas()).isZero();
        assertThat(segunda.ignoradas()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void arquivoCorrompidoViraErroSemInterromperOsDemais() throws IOException {
        Files.writeString(pasta.resolve("quebrado.json"), "{ nao é json");

        var resultado = importador.importar();

        assertThat(resultado.importadas()).isEqualTo(1);
        assertThat(resultado.erros()).singleElement().asString().startsWith("quebrado.json");
    }
}
