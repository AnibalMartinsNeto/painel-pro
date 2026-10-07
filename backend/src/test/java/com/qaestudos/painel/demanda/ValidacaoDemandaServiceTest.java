package com.qaestudos.painel.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.execucao.ResultadoTesteRepository;
import com.qaestudos.painel.execucao.ResultadoTesteRepository.UltimoResultado;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.projeto.TipoProjeto;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ia.IaIndisponivelException;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Relatório de validação com Jira, banco e IA simulados: veredito pelos fatos, texto pela IA. */
class ValidacaoDemandaServiceTest {

    @TempDir
    Path tmp;

    private JiraCliente jira;
    private ResultadoTesteRepository resultados;
    private ConfiguracaoService config;
    private String respostaDaIa;
    private boolean iaFora;
    private ValidacaoDemandaService service;

    private static UltimoResultado resultado(String titulo, String status) {
        UltimoResultado r = mock(UltimoResultado.class);
        when(r.getSpec()).thenReturn("tests/login.spec.js");
        when(r.getTitulo()).thenReturn(titulo);
        when(r.getStatus()).thenReturn(status);
        when(r.getExecucaoId()).thenReturn(7L);
        when(r.getQuando()).thenReturn(Instant.parse("2026-10-06T12:00:00Z"));
        return r;
    }

    @BeforeEach
    void setUp() {
        Projeto pw = new Projeto("playwright", "Playwright", TipoProjeto.PLAYWRIGHT, tmp, "tests", Pattern.compile(".*"), List.of());
        Projeto k6 = new Projeto("k6", "k6", TipoProjeto.K6, tmp, "tests", Pattern.compile(".*"), List.of());
        ProjetoService projetos = mock(ProjetoService.class);
        when(projetos.listar()).thenReturn(List.of(pw, k6));
        when(projetos.specsQueCitam(pw, "DEV-1")).thenReturn(List.of("tests/login.spec.js"));
        when(projetos.specsQueCitam(k6, "DEV-1")).thenReturn(List.of()); // k6 não cita: fica de fora

        resultados = mock(ResultadoTesteRepository.class);
        jira = mock(JiraCliente.class);
        when(jira.buscarDetalhe("DEV-1")).thenReturn(new JiraCliente.DetalheIssue("DEV-1", "Login do admin", "Story", "Aberto",
                "admin loga", "https://x/browse/DEV-1"));
        when(jira.buscarIssue("DEV-1")).thenReturn(new JiraCliente.Issue("DEV-1", "Login do admin", "Story", "Aberto", "https://x/browse/DEV-1"));

        config = mock(ConfiguracaoService.class);
        when(config.valor(ChaveConfig.IA_PROVEDOR)).thenReturn(Optional.of("gemini"));
        when(config.valor(ChaveConfig.IA_GEMINI_MODELO)).thenReturn(Optional.of("gemini-teste"));
        when(config.valor(ChaveConfig.IA_GEMINI_CHAVE)).thenReturn(Optional.of("chave"));
        ProvedorIa iaFalsa = new ProvedorIa() {
            public String id() { return "gemini"; }
            public Resposta gerar(String prompt, String modelo, String chave) {
                if (iaFora) throw new IaIndisponivelException("cota");
                return new Resposta(respostaDaIa, modelo);
            }
        };
        service = new ValidacaoDemandaService(jira, projetos, resultados,
                new AssistenteTriagem(config, List.of(iaFalsa), JsonMapper.builder().build()), config);
    }

    @Test
    void tudoPassouAprovadaEAIaSoDescreveOQueFoiValidado() {
        var passou = List.of(resultado("deve logar", "PASSOU"));
        when(resultados.ultimosResultados(eq("playwright"), any())).thenReturn(passou);
        // A IA "tenta" reprovar no texto, mas o veredito não vem dela.
        respostaDaIa = "{\"veredito\":\"REPROVADA\",\"resumo\":\"Login validado.\",\"validacoes\":[{\"indice\":0,\"oQueFoiValidado\":\"admin chega em /admin/home\"}],\"pendencias\":[\"senha expirada\"]}";

        var r = service.rascunho("dev-1");

        assertThat(r.veredito()).isEqualTo(ValidacaoDemandaService.Veredito.APROVADA);
        assertThat(r.origem()).isEqualTo("IA");
        assertThat(r.itens()).singleElement().satisfies(i -> {
            assertThat(i.projeto()).isEqualTo("Playwright");
            assertThat(i.oQueFoiValidado()).isEqualTo("admin chega em /admin/home");
        });
        assertThat(r.pendencias()).containsExactly("senha expirada");
    }

    @Test
    void algumaFalhaReprovadaEComAIaForaCaiNoRascunhoDireto() {
        var misto = List.of(resultado("deve logar", "PASSOU"), resultado("senha inválida", "FALHOU"));
        when(resultados.ultimosResultados(eq("playwright"), any())).thenReturn(misto);
        iaFora = true;

        var r = service.rascunho("DEV-1");

        assertThat(r.veredito()).isEqualTo(ValidacaoDemandaService.Veredito.REPROVADA);
        assertThat(r.origem()).isEqualTo("HEURISTICA");
        assertThat(r.resumo()).isEqualTo("1 de 2 testes automatizados da demanda falharam na execução mais recente.");
    }

    @Test
    void semExecucaoDosTestesDaDemandaAvisaParaRodarPrimeiro() {
        when(resultados.ultimosResultados(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.rascunho("DEV-1")).hasMessageContaining("Rode os specs da demanda primeiro");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicarComentaORelatorioNaDemanda() {
        var item = new ValidacaoDemandaService.Item("Playwright", "tests/login.spec.js", "deve logar", "PASSOU", 7L,
                Instant.parse("2026-10-06T12:00:00Z"), "admin chega em /admin/home");

        var p = service.publicar("DEV-1", new ValidacaoDemandaService.Publicar(ValidacaoDemandaService.Veredito.APROVADA,
                "Login validado.", List.of(item), List.of()));

        ArgumentCaptor<Map<String, Object>> corpo = ArgumentCaptor.forClass(Map.class);
        verify(jira).comentar(eq("DEV-1"), corpo.capture());
        assertThat(corpo.getValue().toString()).contains("APROVADA", "Login validado.", "deve logar", "execução #7",
                "admin chega em /admin/home");
        assertThat(p.url()).isEqualTo("https://x/browse/DEV-1");
    }
}
