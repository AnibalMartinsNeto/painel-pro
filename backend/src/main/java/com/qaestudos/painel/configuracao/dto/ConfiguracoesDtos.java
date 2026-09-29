package com.qaestudos.painel.configuracao.dto;

import java.util.List;

/**
 * Contrato da API de configurações.
 *
 * <p>Repare na ASSIMETRIA: a resposta (GET) nunca traz o valor de um
 * segredo, só se ele está configurado; o pedido (PUT) aceita o segredo,
 * mas é "somente escrita". É o padrão para senhas e chaves em qualquer
 * API: o servidor recebe, guarda e usa, mas nunca devolve.
 */
public final class ConfiguracoesDtos {

    private ConfiguracoesDtos() {}

    public record ConfiguracoesResponse(String ambiente, IaResponse ia, JiraResponse jira) {}

    public record IaResponse(
            String provedor, String modeloAnthropic, String modeloGemini,
            boolean anthropicConfigurada, boolean geminiConfigurada,
            boolean ativa) {} // o provedor escolhido tem chave?

    public record JiraResponse(String url, String email, String projeto, String tipoIssue, boolean tokenConfigurado, boolean configurado) {}

    /**
     * Pedido de atualização. Campos {@code null} não mudam nada. Em campos
     * comuns, texto vazio apaga; em SEGREDOS, vazio é ignorado (para apagar
     * um segredo, informe a chave em {@code remover}).
     */
    public record AtualizarConfiguracoesRequest(String ambiente, IaRequest ia, JiraRequest jira, List<String> remover) {}

    public record IaRequest(String provedor, String modeloAnthropic, String modeloGemini, String chaveAnthropic, String chaveGemini) {
        @Override
        public String toString() { // nunca imprime as chaves (ex.: em logs de erro)
            return "IaRequest[provedor=%s, modeloAnthropic=%s, modeloGemini=%s, chaveAnthropic=%s, chaveGemini=%s]"
                    .formatted(provedor, modeloAnthropic, modeloGemini, mascarar(chaveAnthropic), mascarar(chaveGemini));
        }
    }

    public record JiraRequest(String url, String email, String projeto, String tipoIssue, String token) {
        @Override
        public String toString() {
            return "JiraRequest[url=%s, email=%s, projeto=%s, tipoIssue=%s, token=%s]".formatted(url, email, projeto, tipoIssue, mascarar(token));
        }
    }

    public record ImportacaoConfiguracoesResponse(List<String> importadas, String aviso) {}

    static String mascarar(String segredo) {
        return segredo == null || segredo.isEmpty() ? String.valueOf(segredo) : "****";
    }
}
