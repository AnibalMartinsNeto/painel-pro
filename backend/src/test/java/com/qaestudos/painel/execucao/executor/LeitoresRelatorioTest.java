package com.qaestudos.painel.execucao.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.StatusTeste;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Leitura dos relatórios de cada ferramenta, a partir de JSONs de exemplo
 * no formato real (reduzidos). Se uma ferramenta mudar o formato numa nova
 * versão, é aqui que o teste acusa.
 */
class LeitoresRelatorioTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Nested
    class Cypress {

        @Test
        void converteTestesDoCypressRun() {
            var leitura = CypressExecutor.interpretar(json.readTree("""
                    {"ok":true,"results":{"cypressVersion":"15.19.0","runs":[
                      {"spec":{"relative":"cypress\\\\e2e\\\\login.cy.js"},"tests":[
                        {"title":["Login","válido"],"state":"passed","duration":812},
                        {"title":["Login","bloqueado"],"state":"failed","duration":4100,
                         "displayError":"AssertionError: expected 'x' to contain 'locked'"}]}]}}
                    """));

            assertThat(leitura.erro()).isNull();
            assertThat(leitura.versaoFerramenta()).isEqualTo("Cypress 15.19.0");
            assertThat(leitura.resultados()).extracting(ResultadoTeste::getSpec).containsOnly("cypress/e2e/login.cy.js");
            ResultadoTeste falha = leitura.resultados().get(1);
            assertThat(falha.getTitulo()).isEqualTo("Login › bloqueado");
            assertThat(falha.getStatus()).isEqualTo(StatusTeste.FALHOU);
            assertThat(falha.getTipoErro()).isEqualTo("Asserção");
        }

        @Test
        void cypressQueNaoSubiuViraErroDaExecucao() {
            var leitura = CypressExecutor.interpretar(json.readTree("""
                    {"ok":true,"results":{"status":"failed","message":"Browser 'safari' not found"}}
                    """));
            assertThat(leitura.resultados()).isEmpty();
            assertThat(leitura.erro()).contains("safari");
        }
    }

    @Nested
    class Playwright {

        @Test
        void percorreSuitesAninhadasEUsaAUltimaTentativa() {
            var leitura = PlaywrightExecutor.interpretar(json.readTree("""
                    {"config":{"rootDir":"C:/proj/tests","version":"1.63.0"},"errors":[],
                     "suites":[{"title":"login.spec.js","file":"login.spec.js","specs":[],"suites":[
                       {"title":"Login","specs":[
                         {"title":"válido","tests":[{"status":"expected","results":[{"duration":500,"errors":[]}]}]},
                         {"title":"instável","tests":[{"status":"flaky","results":[{"duration":100,"errors":[{"message":"x"}]},{"duration":200,"errors":[]}]}]},
                         {"title":"quebrado","tests":[{"status":"unexpected","results":[
                           {"duration":900,"errors":[{"message":"\\u001b[31mError: expect(locator).toBeVisible() failed\\u001b[39m element(s) not found"}]}]}]}
                       ]}]}]}
                    """), Path.of("C:/proj"));

            assertThat(leitura.versaoFerramenta()).isEqualTo("Playwright 1.63.0");
            assertThat(leitura.resultados()).extracting(ResultadoTeste::getTitulo)
                    .containsExactly("Login › válido", "Login › instável", "Login › quebrado");
            assertThat(leitura.resultados()).extracting(ResultadoTeste::getStatus)
                    .containsExactly(StatusTeste.PASSOU, StatusTeste.PASSOU, StatusTeste.FALHOU); // flaky passou na retentativa
            ResultadoTeste falha = leitura.resultados().get(2);
            assertThat(falha.getSpec()).isEqualTo("tests/login.spec.js");
            assertThat(falha.getMensagemErro()).doesNotContain("\u001b"); // cores ANSI removidas
            assertThat(falha.getTipoErro()).isEqualTo("Elemento / Seletor");
            assertThat(leitura.resultados().get(1).getDuracaoMs()).isEqualTo(300L); // soma das tentativas
        }
    }

    @Nested
    class K6 {

        @Test
        void checksEThresholdsViramResultados() {
            var resultados = K6Executor.interpretar(json.readTree("""
                    {"root_group":{"name":"","checks":{},"groups":{
                       "Página inicial":{"name":"Página inicial","groups":{},"checks":{
                         "status 200":{"name":"status 200","passes":3,"fails":0},
                         "abaixo de 1500ms":{"name":"abaixo de 1500ms","passes":2,"fails":1}}}}},
                     "metrics":{
                       "http_req_duration":{"avg":120,"p(95)":1800,"max":2100,"thresholds":{"p(95)<1500":true}},
                       "http_req_failed":{"value":0,"thresholds":{"rate<0.01":false}}}}
                    """), "tests/smoke.js");

            assertThat(resultados).extracting(ResultadoTeste::getTitulo).containsExactly(
                    "Página inicial › status 200",
                    "Página inicial › abaixo de 1500ms",
                    "Thresholds › http_req_duration: p(95)<1500",
                    "Thresholds › http_req_failed: rate<0.01");
            assertThat(resultados).extracting(ResultadoTeste::getStatus).containsExactly(
                    StatusTeste.PASSOU, StatusTeste.FALHOU, StatusTeste.FALHOU, StatusTeste.PASSOU);
            assertThat(resultados.get(1).getMensagemErro()).isEqualTo("1 de 3 verificações falharam.");
            assertThat(resultados.get(2).getTipoErro()).isEqualTo("Desempenho / Threshold");
            assertThat(resultados.get(2).getMensagemErro()).contains("p95 1800ms");
        }
    }
}
