package com.qaestudos.painel.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Modulo;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.projeto.TipoProjeto;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
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

/** Mapa de cobertura com Jira e IA simulados: monta o prompt certo e lê a resposta. */
class CoberturaDemandaServiceTest {

    @TempDir
    Path tmp;

    private ProjetoService projetos;
    private JiraCliente jira;
    private ConfiguracaoService config;
    private Projeto projeto;
    private String promptRecebido;
    private String respostaDaIa;
    private CoberturaDemandaService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(tmp.resolve("tests"));
        Files.writeString(tmp.resolve("tests/login.spec.js"), """
                test.describe("Login [DEV-1]", () => {
                  test("deve logar como administrador", async ({ loginPage }) => {
                    const x = algoSemImportancia();
                    await loginPage.logar(email, senha);
                    await expect(page).toHaveURL(/admin/);
                  });
                });
                """);
        projeto = new Projeto("playwright", "Playwright", TipoProjeto.PLAYWRIGHT, tmp, "tests", Pattern.compile(".*"), List.of(),
                null, List.of(), List.of(new Modulo("Login", "login", "Login e autenticação")));

        projetos = mock(ProjetoService.class);
        when(projetos.buscar("playwright")).thenReturn(projeto);
        when(projetos.listar()).thenReturn(List.of(projeto));
        when(projetos.regras(projeto)).thenReturn(Optional.of("- **LOG-02** – senha errada retorna 401"));
        when(projetos.listarSpecs(projeto)).thenReturn(List.of("tests/login.spec.js", "tests/loja.spec.js"));

        jira = mock(JiraCliente.class);
        when(jira.buscarDetalhe("DEV-1")).thenReturn(new JiraCliente.DetalheIssue("DEV-1", "Login do administrador", "Story",
                "Aberto", "- admin loga\n- senha errada mostra erro", "https://x/browse/DEV-1"));

        config = mock(ConfiguracaoService.class);
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
        service = new CoberturaDemandaService(jira, projetos,
                new AssistenteTriagem(config, List.of(iaFalsa), JsonMapper.builder().build()));
    }

    @Test
    void usaOsSpecsQueCitamADemandaEDevolveORequisitoAPorRequisito() {
        when(projetos.specsQueCitam(projeto, "DEV-1")).thenReturn(List.of("tests/login.spec.js"));
        respostaDaIa = """
                ```json
                {"resumo":"Metade coberta.","requisitos":[
                  {"requisito":"admin loga","situacao":"COBERTO","evidencias":["tests/login.spec.js › deve logar como administrador"],"cenarioSugerido":null},
                  {"requisito":"senha errada mostra erro","situacao":"sem_teste","evidencias":[],"cenarioSugerido":"Logar com senha errada"}
                ]}
                ```""";

        var c = service.mapear("playwright", " dev-1 ");

        assertThat(promptRecebido).contains("Demanda DEV-1 (Story) — Login do administrador", "senha errada mostra erro", "LOG-02",
                        "Spec (Playwright): tests/login.spec.js", "loginPage.logar(email, senha)", "toHaveURL")
                .doesNotContain("algoSemImportancia"); // vai o RESUMO do spec, não o arquivo inteiro
        assertThat(c.criterioSpecs()).startsWith("specs que citam DEV-1");
        assertThat(c.specsAnalisados()).containsExactly("playwright: tests/login.spec.js");
        assertThat(c.resumo()).isEqualTo("Metade coberta.");
        assertThat(c.requisitos()).extracting(CoberturaDemandaService.Requisito::situacao)
                .containsExactly(CoberturaDemandaService.Situacao.COBERTO, CoberturaDemandaService.Situacao.SEM_TESTE);
        assertThat(c.requisitos().getFirst().evidencias()).containsExactly("tests/login.spec.js › deve logar como administrador");
        assertThat(c.modelo()).isEqualTo("gemini-teste");
    }

    @Test
    void semSpecQueCiteADemandaUsaOsDoModuloCitadoNoTitulo() {
        when(projetos.specsQueCitam(any(), any())).thenReturn(List.of());
        respostaDaIa = "{\"resumo\":\"x\",\"requisitos\":[]}";

        var c = service.mapear("playwright", "DEV-1");

        assertThat(c.specsAnalisados()).containsExactly("playwright: tests/login.spec.js"); // "Login" está no título da demanda
        assertThat(c.criterioSpecs()).contains("módulos citados", "Login");
    }

    @Test
    void chaveInvalidaOuSemIaDaMensagemClara() {
        assertThatThrownBy(() -> service.mapear("playwright", "login")).isInstanceOf(RequisicaoInvalidaException.class);

        when(projetos.specsQueCitam(any(), any())).thenReturn(List.of("tests/login.spec.js"));
        when(config.valor(ChaveConfig.IA_GEMINI_CHAVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.mapear("playwright", "DEV-1")).hasMessageContaining("Configure uma chave de IA");
    }
}
