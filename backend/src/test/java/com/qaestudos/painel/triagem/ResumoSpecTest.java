package com.qaestudos.painel.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumoSpecTest {

    @Test
    void specPequenoVaiInteiro() {
        String spec = "test(\"a\", () => { expect(1).toBe(1); });";
        assertThat(ResumoSpec.paraPrompt(spec, "a")).isEqualTo(spec);
        assertThat(ResumoSpec.paraPrompt(null, "a")).isNull();
    }

    @Test
    void estruturaReconheceK6GruposChecksHttpELimites() {
        String k6 = """
                import http from "k6/http";
                const carga = { vus: 1 };
                export const options = {
                  thresholds: { "http_req_duration": ["p(95)<1500"], checks: ["rate>0.99"] },
                };
                export default function () {
                  group("Login válido", () => {
                    const res = http.post(`${API}/login`, corpo);
                    check(res, {
                      "login: status 200": (r) => r.status === 200,
                    });
                  });
                }
                """;

        assertThat(ResumoSpec.estrutura(k6)).contains("thresholds", "p(95)<1500", "group(\"Login válido\"", "http.post(",
                "\"login: status 200\": (r) => r.status === 200").doesNotContain("const carga");
    }

    @Test
    void specGrandeVaiComOBlocoDoTesteInteiroEUmResumoDoResto() {
        StringBuilder spec = new StringBuilder("test.describe(\"Admin @usuarios\", () => {\n");
        spec.append("  test.beforeEach(async ({ api }) => { await api.garantirUsuario(admin); });\n");
        for (int i = 0; i < 80; i++) {
            spec.append("  test(\"outro teste ").append(i).append("\", async ({ adminUsuariosPage }) => {\n")
                    .append("    const x = calcularAlgoComplicado(").append(i).append(");   // linha sem interesse\n")
                    .append("    await adminUsuariosPage.visitarCadastro();\n")
                    .append("    await expect(page).toHaveURL(/cadastrar/);\n")
                    .append("  });\n");
        }
        spec.append("  test(\"lista não deve exibir a senha\", async ({ adminUsuariosPage }) => {\n")
                .append("    const detalheSoDesteTeste = 42;\n")
                .append("    await adminUsuariosPage.visitarLista();\n")
                .append("  });\n});\n");

        String r = ResumoSpec.paraPrompt(spec.toString(), "Admin @usuarios › lista não deve exibir a senha");

        assertThat(r).contains("Teste que falhou (bloco completo):", "const detalheSoDesteTeste = 42;", "visitarLista()")
                .contains("Resumo do restante do spec", "beforeEach", "adminUsuariosPage.visitarCadastro()", "toHaveURL")
                .doesNotContain("calcularAlgoComplicado"); // linha sem estrutura/asserção/Page Object fica de fora
        assertThat(r).containsPattern("\\s+\\d+\\| ");      // com número de linha
        assertThat(r.length()).isLessThan(spec.length());
    }
}
