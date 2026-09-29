package com.qaestudos.painel.jira;

import static com.qaestudos.painel.configuracao.ChaveConfig.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente da API REST do Jira Cloud (v3).
 *
 * <p>Autenticação "Basic": o cabeçalho {@code Authorization} leva
 * {@code base64(email:token)}. Por isso o Jira precisa do e-mail E do
 * token — o token sozinho não diz de quem é a conta. Tudo por HTTPS.
 *
 * <p>O cliente é montado a cada uso com as configurações atuais: mudou o
 * token na tela, a próxima chamada já usa o novo, sem reiniciar nada.
 */
@Component
public class JiraCliente {

    private final ConfiguracaoService config;
    private final RestClient.Builder builder;

    // ObjectProvider: usa o RestClient.Builder do Spring se existir (nos
    // testes, um builder ligado a um servidor falso); senão, cria um.
    public JiraCliente(ConfiguracaoService config, ObjectProvider<RestClient.Builder> builder) {
        this.config = config;
        this.builder = builder.getIfAvailable(() -> RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build())));
    }

    public record TesteConexao(String usuario, String email, String projeto, String nomeProjeto) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usuario(String displayName, String emailAddress) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Projeto(String key, String name) {}

    /** Confere as credenciais (GET /myself) e o acesso ao projeto (GET /project/{chave}). */
    public TesteConexao testarConexao() {
        String projeto = obrigatorio(JIRA_PROJETO, "chave do projeto");
        RestClient jira = cliente();
        try {
            Usuario eu = jira.get().uri("/rest/api/3/myself").retrieve().body(Usuario.class);
            Projeto p = jira.get().uri("/rest/api/3/project/{chave}", projeto).retrieve().body(Projeto.class);
            return new TesteConexao(eu.displayName(), eu.emailAddress(), p.key(), p.name());
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RequisicaoInvalidaException("O Jira recusou as credenciais (401). Confira o e-mail e o API token.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new RequisicaoInvalidaException("Projeto '%s' não encontrado, ou a conta não tem acesso a ele (404).".formatted(projeto));
        } catch (HttpClientErrorException e) {
            throw new RequisicaoInvalidaException("O Jira respondeu %s.".formatted(e.getStatusCode()));
        } catch (ResourceAccessException e) {
            throw new RequisicaoInvalidaException("Não foi possível conectar ao Jira em %s. Confira a URL.".formatted(texto(JIRA_URL)));
        }
    }

    /** Uma issue do Jira, no formato que o painel usa. */
    public record Issue(String chave, String resumo, String tipo, String status, String url) {}

    /** Dados do bug a criar. prioridade: Highest, High, Medium, Low ou Lowest. */
    public record NovoBug(String resumo, Map<String, Object> descricao, String prioridade, List<String> etiquetas) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssueJson(String key, Campos fields) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Campos(String summary, Nome issuetype, Nome status) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Nome(String name) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Criada(String key) {}

    /** GET /issue/{chave}: a demanda que os testes validam (ex.: DEV-1). */
    public Issue buscarIssue(String chave) {
        try {
            IssueJson i = cliente().get().uri("/rest/api/3/issue/{chave}?fields=summary,issuetype,status", chave)
                    .retrieve().body(IssueJson.class);
            return new Issue(i.key(), i.fields().summary(), i.fields().issuetype().name(), i.fields().status().name(), link(i.key()));
        } catch (HttpClientErrorException.NotFound e) {
            throw new RequisicaoInvalidaException("Issue '%s' não existe no Jira (ou a conta não tem acesso).".formatted(chave));
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RequisicaoInvalidaException("O Jira recusou as credenciais (401). Confira o e-mail e o API token.");
        } catch (ResourceAccessException e) {
            throw new RequisicaoInvalidaException("Não foi possível conectar ao Jira em %s.".formatted(texto(JIRA_URL)));
        }
    }

    /** POST /issue: cria o bug no projeto configurado e devolve chave + link. */
    public Issue criarBug(NovoBug bug) {
        String projeto = obrigatorio(JIRA_PROJETO, "chave do projeto");
        String tipo = config.valor(JIRA_TIPO_ISSUE).orElse("Bug");
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("project", Map.of("key", projeto));
        campos.put("issuetype", Map.of("name", tipo));
        campos.put("summary", bug.resumo().length() > 250 ? bug.resumo().substring(0, 250) : bug.resumo());
        campos.put("description", bug.descricao());
        if (bug.prioridade() != null) campos.put("priority", Map.of("name", bug.prioridade()));
        campos.put("labels", bug.etiquetas());
        try {
            Criada c = cliente().post().uri("/rest/api/3/issue").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", campos)).retrieve().body(Criada.class);
            return new Issue(c.key(), bug.resumo(), tipo, null, link(c.key()));
        } catch (HttpClientErrorException.BadRequest e) {
            // O Jira devolve {"errors":{"campo":"motivo"}}: repassa para o QA entender o que ajustar.
            throw new RequisicaoInvalidaException("O Jira recusou o bug: " + e.getResponseBodyAsString());
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new RequisicaoInvalidaException("Sem permissão para criar issues no projeto %s (%s).".formatted(projeto, e.getStatusCode()));
        } catch (ResourceAccessException e) {
            throw new RequisicaoInvalidaException("Não foi possível conectar ao Jira em %s.".formatted(texto(JIRA_URL)));
        }
    }

    /** POST /issueLink: "bug relaciona-se a demanda" (tipo de ligação padrão "Relates"). */
    public void vincular(String bug, String demanda) {
        cliente().post().uri("/rest/api/3/issueLink").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", Map.of("name", "Relates"), "inwardIssue", Map.of("key", bug), "outwardIssue", Map.of("key", demanda)))
                .retrieve().toBodilessEntity();
    }

    private String link(String chave) {
        return texto(JIRA_URL) + "/browse/" + chave;
    }

    /** Monta o cliente com URL base e cabeçalho de autenticação a partir das configurações. */
    RestClient cliente() {
        String url = obrigatorio(JIRA_URL, "URL");
        String email = obrigatorio(JIRA_EMAIL, "e-mail");
        String token = obrigatorio(JIRA_TOKEN, "API token");
        String credencial = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        return builder.clone()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credencial)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String obrigatorio(ChaveConfig chave, String nome) {
        return config.valor(chave).filter(v -> !v.isBlank()).orElseThrow(() ->
                new RequisicaoInvalidaException("Jira não configurado: falta o campo '%s' em Configurações.".formatted(nome)));
    }

    private String texto(ChaveConfig chave) {
        return config.valor(chave).orElse("");
    }
}
