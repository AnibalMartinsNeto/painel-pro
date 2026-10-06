package com.qaestudos.painel.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O buscador com uma réplica pequena da estrutura real: um spec Playwright que
 * importa um Page Object (onde está a rota), e um front React em que a rota
 * aponta para a tela (o arquivo que tem o defeito, sem citar a rota).
 */
class BuscadorCodigoTest {

    @TempDir
    Path tmp;

    private static final String TITULO = "Admin › lista de usuários não deve exibir a senha";

    private Path projeto;
    private Path front;
    private String spec;

    private void escrever(Path arquivo, String conteudo) throws IOException {
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, conteudo);
    }

    @BeforeEach
    void setUp() throws IOException {
        projeto = tmp.resolve("playwright");
        escrever(projeto.resolve("pages/AdminUsuariosPage.js"), """
                class AdminUsuariosPage {
                  async visitarLista() { await this.page.goto("/admin/listarusuarios"); }
                  botao() { return this.page.getByTestId("cadastrarUsuario"); }
                }
                """);
        escrever(projeto.resolve("support/fixtures.js"), """
                const { AdminUsuariosPage } = require("../pages/AdminUsuariosPage");
                """);
        spec = """
                const { test, expect } = require("../support/fixtures");
                test("lista de usuários não deve exibir a senha", async ({ adminUsuariosPage }) => {
                  await adminUsuariosPage.visitarLista();
                  await expect(linha).not.toContainText(usuario.senha);
                });
                """;
        escrever(projeto.resolve("tests/admin-usuarios.spec.js"), spec);

        front = tmp.resolve("ServeRest-front");
        escrever(front.resolve("src/App.js"), """
                import ShowUsers from './views/admin/showUsers';
                import Login from './views/login';
                export default function App() {
                  return (<Switch>
                    <Route exact path="/login" component={ Login } />
                    <Route exact path="/admin/listarusuarios" component={ ShowUsers } />
                  </Switch>);
                }
                """);
        escrever(front.resolve("src/views/admin/showUsers.js"), """
                export default function ShowUsers({ users }) {
                  return users.map((person) => (
                    <tr>
                      <td>{ person.nome }</td>
                      <td>{ person.password }</td>
                    </tr>));
                }
                """);
        escrever(front.resolve("src/views/login.js"), "export default function Login() { return <form/>; }\n");
        escrever(front.resolve("node_modules/lib/index.js"), "// /admin/listarusuarios  (não pode ser lido)\n");
        escrever(front.resolve("src/App.test.js"), "// /admin/listarusuarios  (teste do próprio front: ignorado)\n");
    }

    @Test
    void segueARotaDoPageObjectAteATelaQueTemODefeito() {
        var r = BuscadorCodigo.buscar(projeto, "tests/admin-usuarios.spec.js", spec, TITULO, "Expected substring: not \"SenhaSecreta#2026\"",
                List.of(front));

        assertThat(r.termos()).contains("/admin/listarusuarios", "cadastrarUsuario"); // vieram do Page Object importado
        assertThat(r.trechos()).extracting(BuscadorCodigo.Trecho::arquivo)
                .startsWith("ServeRest-front/src/views/admin/showUsers.js") // a tela da rota vem primeiro
                .contains("ServeRest-front/src/App.js")
                .doesNotContain("ServeRest-front/src/views/login.js", "ServeRest-front/node_modules/lib/index.js", "ServeRest-front/src/App.test.js");
        assertThat(r.formatar()).contains("   5| ", "<td>{ person.password }</td>"); // com número de linha
    }

    @Test
    void semPastasDoSistemaOuSemCodigoDoSpecNaoBuscaNada() {
        assertThat(BuscadorCodigo.buscar(projeto, "tests/admin-usuarios.spec.js", spec, TITULO, "", List.of()).vazio()).isTrue();
        assertThat(BuscadorCodigo.buscar(projeto, "tests/admin-usuarios.spec.js", null, TITULO, "", List.of(front)).vazio()).isTrue();
        assertThat(BuscadorCodigo.buscar(projeto, "tests/admin-usuarios.spec.js", spec, TITULO, "", List.of(tmp.resolve("nao-existe"))).vazio()).isTrue();
    }

    @Test
    void textosDaTelaEntramComoTermoMasSeletoresECaminhosNao() {
        String codigo = """
                cy.contains("Este email já está sendo usado");
                cy.get('[data-testid="email"]');
                cy.visit("/cadastrarusuarios");
                """;
        var termos = BuscadorCodigo.extrairTermos(projeto, projeto.resolve("x.spec.js"), codigo, codigo, "AssertionError: expected 'Lista dos usuários' to be visible");

        assertThat(termos.textos()).contains("Este email já está sendo usado", "Lista dos usuários")
                .noneMatch(t -> t.contains("data-testid") || t.startsWith("/"));
        assertThat(termos.rotas()).contains("/cadastrarusuarios");
        assertThat(termos.testIds()).contains("email");
    }

    @Test
    void blocoDoTesteRecortaSoOTesteQueFalhou() {
        String codigo = """
                test.describe("Admin", () => {
                  test("admin deve cadastrar usuário", async ({ adminUsuariosPage }) => {
                    await adminUsuariosPage.visitarCadastro();
                  });
                  test("lista de usuários não deve exibir a senha", async ({ adminUsuariosPage }) => {
                    await adminUsuariosPage.visitarLista();
                  });
                });
                """;

        String bloco = BuscadorCodigo.blocoDoTeste(codigo, TITULO);

        assertThat(bloco).contains("visitarLista").doesNotContain("visitarCadastro");
        assertThat(BuscadorCodigo.blocoDoTeste(codigo, "título que não existe")).isEqualTo(codigo);
    }
}
