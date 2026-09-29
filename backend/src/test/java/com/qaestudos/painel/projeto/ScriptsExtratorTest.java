package com.qaestudos.painel.projeto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Testes de unidade da interpretação dos comandos de cada ferramenta. */
class ScriptsExtratorTest {

    private static Map<String, String> scripts(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }

    @Test
    void cypressLeSpecsEntreAspasENavegador() {
        var r = ScriptsExtrator.extrair(TipoProjeto.CYPRESS,
                scripts("test:chrome", "cypress run --browser chrome --spec \"a.cy.js,b.cy.js\""), List.of());

        assertThat(r).singleElement().satisfies(s -> {
            assertThat(s.specs()).containsExactly("a.cy.js", "b.cy.js");
            assertThat(s.navegador()).isEqualTo("chrome");
        });
    }

    @Test
    void playwrightIgnoraModoUiECasaArquivosComOsSpecs() {
        var specs = List.of("tests/login.spec.js", "tests/checkout.spec.js");
        var r = ScriptsExtrator.extrair(TipoProjeto.PLAYWRIGHT, scripts(
                "pw:ui", "playwright test --ui",
                "test", "playwright test tests/login.spec.js --project=chromium",
                "test:all", "playwright test --project=firefox"), specs);

        assertThat(r).extracting(ScriptExecucao::nome).containsExactly("test", "test:all");
        assertThat(r.get(0).specs()).containsExactly("tests/login.spec.js");
        assertThat(r.get(0).navegador()).isEqualTo("chromium");
        assertThat(r.get(1).specs()).isEqualTo(specs);
    }

    @Test
    void k6MantemAOrdemDoComandoEncadeado() {
        var specs = List.of("tests/browser.js", "tests/load.js", "tests/smoke.js");
        var r = ScriptsExtrator.extrair(TipoProjeto.K6,
                scripts("test:all", "k6 run tests/smoke.js && k6 run ./tests/load.js"), specs);

        assertThat(r).singleElement()
                .extracting(ScriptExecucao::specs)
                .isEqualTo(List.of("tests/smoke.js", "tests/load.js"));
    }
}
