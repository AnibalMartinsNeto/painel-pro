package com.qaestudos.painel.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.projeto.TipoProjeto;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CasoTesteServiceTest {

    @TempDir
    Path tmp;

    private JiraCliente jira;
    private String promptRecebido;
    private String respostaDaIa;
    private CasoTesteService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(tmp.resolve("tests"));
        Files.writeString(tmp.resolve("tests/login.spec.js"), """
                test.describe("Login [DEV-1]", () => {
                  test("deve logar", async ({ loginPage }) => { await loginPage.logar(a, b); await expect(page).toHaveURL(/admin/); });
                });
                """);
        Projeto pw = new Projeto("playwright", "Playwright", TipoProjeto.PLAYWRIGHT, tmp, "tests", Pattern.compile(".*"), List.of());
        ProjetoService projetos = mock(ProjetoService.class);
        when(projetos.buscar("playwright")).thenReturn(pw);
        when(projetos.listar()).thenReturn(List.of(pw));
        when(projetos.specsQueCitam(pw, "DEV-1")).thenReturn(List.of("tests/login.spec.js"));
        when(projetos.regras(pw)).thenReturn(Optional.of("- **LOG-02** – senha errada retorna 401"));

        jira = mock(JiraCliente.class);
        when(jira.buscarDetalhe("DEV-1")).thenReturn(new JiraCliente.DetalheIssue("DEV-1", "Login do admin", "Story", "Aberto",
                "admin loga; senha errada mostra erro", "https://x/browse/DEV-1"));
        when(jira.buscarIssue("DEV-1")).thenReturn(new JiraCliente.Issue("DEV-1", "Login do admin", "Story", "Aberto", "https://x/browse/DEV-1"));

        ConfiguracaoService config = mock(ConfiguracaoService.class);
        when(config.valor(ChaveConfig.IA_PROVEDOR)).thenReturn(Optional.of("gemini"));
        when(config.valor(ChaveConfig.IA_GEMINI_MODELO)).thenReturn(Optional.of("gemini-teste"));
        when(config.valor(ChaveConfig.IA_GEMINI_CHAVE)).thenReturn(Optional.of("chave"));
        ProvedorIa iaFalsa = new ProvedorIa() {
            public String id() { return "gemini"; }
            public Resposta gerar(String prompt, String modelo, String chave) {
                promptRecebido = prompt;
                return new Resposta(respostaDaIa, modelo);
            }
        };
        service = new CasoTesteService(jira, projetos, new AssistenteTriagem(config, List.of(iaFalsa), JsonMapper.builder().build()));
    }

    @Test
    void rascunhoUsaDemandaRegrasETestesExistentesELeOsCasos() {
        respostaDaIa = """
                {"casos":[
                  {"titulo":"Login válido","tipo":"POSITIVO","preCondicoes":"admin cadastrado","passos":["abrir /login","entrar"],
                   "resultadoEsperado":"vai para /admin/home","regra":"LOG-04","automatizado":true,"evidencia":"tests/login.spec.js › deve logar"},
                  {"titulo":"Senha errada","tipo":"negativo","preCondicoes":null,"passos":["entrar com senha errada"],
                   "resultadoEsperado":"erro","regra":"null","automatizado":false,"evidencia":null}]}
                """;

        var r = service.rascunho("playwright", "dev-1");

        assertThat(promptRecebido).contains("Demanda DEV-1 (Story) — Login do admin", "senha errada mostra erro", "LOG-02",
                "Spec (Playwright): tests/login.spec.js", "loginPage.logar");
        assertThat(r.casos()).hasSize(2);
        assertThat(r.casos().get(0).automatizado()).isTrue();
        assertThat(r.casos().get(1).tipo()).isEqualTo(CasoTesteService.TipoCaso.NEGATIVO); // tolera minúscula
        assertThat(r.casos().get(1).regra()).isNull();                                      // "null" escrito pela IA
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicarComentaOsCasosRevisadosNaDemanda() {
        var caso = new CasoTesteService.Caso("Senha errada", CasoTesteService.TipoCaso.NEGATIVO, "admin cadastrado",
                List.of("abrir /login", "entrar com senha errada"), "mostra 'Email e/ou senha inválidos'", "LOG-05", false, null);

        var p = service.publicar("DEV-1", List.of(caso, new CasoTesteService.Caso(" ", null, null, List.of(), null, null, false, null)));

        ArgumentCaptor<Map<String, Object>> corpo = ArgumentCaptor.forClass(Map.class);
        verify(jira).comentar(eq("DEV-1"), corpo.capture());
        assertThat(corpo.getValue().toString()).contains("Casos de teste do QA (1)", "CT01 — Senha errada [NEGATIVO] · LOG-05",
                "entrar com senha errada", "Ainda sem teste automatizado");
        assertThat(p.casos()).isEqualTo(1); // o caso sem título foi descartado
        assertThatThrownBy(() -> service.publicar("DEV-1", List.of())).hasMessageContaining("Não há casos");
    }
}
