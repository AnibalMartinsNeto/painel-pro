package com.qaestudos.painel.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qaestudos.painel.TestcontainersConfiguration;
import com.qaestudos.painel.common.RequisicaoInvalidaException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Configurações contra um PostgreSQL real: o que interessa é o que fica GRAVADO no banco. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ConfiguracaoServiceTest {

    @Autowired
    ConfiguracaoRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestEntityManager em;

    private ConfiguracaoService service;

    @BeforeEach
    void setUp() {
        byte[] chave = new byte[32];
        Arrays.fill(chave, (byte) 3);
        service = new ConfiguracaoService(repository, new Cifrador(chave), Clock.systemUTC());
    }

    /** Lê a coluna crua via SQL, sem passar pela aplicação — o que um invasor com acesso ao banco veria. */
    private String valorCruNoBanco(ChaveConfig chave) {
        em.flush();
        return jdbc.queryForObject("SELECT valor FROM configuracao WHERE chave = ?", String.class, chave.chave());
    }

    @Test
    void segredoFicaCriptografadoNoBancoMasVoltaLegivelPelaAplicacao() {
        service.definir(ChaveConfig.IA_GEMINI_CHAVE, "AQ.chave-de-teste");

        assertThat(valorCruNoBanco(ChaveConfig.IA_GEMINI_CHAVE)).startsWith("v1:").doesNotContain("chave-de-teste");
        assertThat(service.valor(ChaveConfig.IA_GEMINI_CHAVE)).contains("AQ.chave-de-teste");
    }

    @Test
    void valorComumFicaEmTextoPuro() {
        service.definir(ChaveConfig.JIRA_EMAIL, "qa@empresa.com");
        assertThat(valorCruNoBanco(ChaveConfig.JIRA_EMAIL)).isEqualTo("qa@empresa.com");
    }

    @Test
    void semValorGravadoUsaOPadraoDoCatalogo() {
        assertThat(service.valor(ChaveConfig.IA_GEMINI_MODELO)).contains("gemini-flash-latest");
        assertThat(service.valor(ChaveConfig.JIRA_TOKEN)).isEmpty();
        assertThat(service.configurado(ChaveConfig.JIRA_TOKEN)).isFalse();
    }

    @Test
    void segredoEmBrancoNaoApagaOAtual() {
        service.definir(ChaveConfig.JIRA_TOKEN, "token-original");
        service.definir(ChaveConfig.JIRA_TOKEN, "   ");
        assertThat(service.valor(ChaveConfig.JIRA_TOKEN)).contains("token-original");
    }

    @Test
    void removerApagaDeVerdade() {
        service.definir(ChaveConfig.JIRA_TOKEN, "pat");
        service.atualizar(Map.of(), Set.of(ChaveConfig.JIRA_TOKEN));
        assertThat(service.configurado(ChaveConfig.JIRA_TOKEN)).isFalse();
    }

    @Test
    void provedorDeIaInvalidoERecusado() {
        assertThatThrownBy(() -> service.definir(ChaveConfig.IA_PROVEDOR, "chatgpt"))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("gemini ou anthropic");
    }

    @Test
    void urlDoJiraExigeHttpsEPerdeABarraFinal() {
        assertThatThrownBy(() -> service.definir(ChaveConfig.JIRA_URL, "http://empresa.atlassian.net"))
                .hasMessageContaining("https://");

        service.definir(ChaveConfig.JIRA_URL, "https://empresa.atlassian.net//");
        assertThat(service.valor(ChaveConfig.JIRA_URL)).contains("https://empresa.atlassian.net");
    }

    @Test
    void chaveDoProjetoJiraViraMaiuscula() {
        service.definir(ChaveConfig.JIRA_PROJETO, "qa");
        assertThat(service.valor(ChaveConfig.JIRA_PROJETO)).contains("QA");
    }

    @Test
    void chaveDesconhecidaERecusada() {
        assertThatThrownBy(() -> ConfiguracaoService.chavePorNome("senha.do.banco"))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }
}
