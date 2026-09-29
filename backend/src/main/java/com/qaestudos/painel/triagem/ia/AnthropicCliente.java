package com.qaestudos.painel.triagem.ia;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Anthropic (Claude): POST /v1/messages, chave em x-api-key e versão da API em anthropic-version. */
@Component
public class AnthropicCliente implements ProvedorIa {

    static final String URL = "https://api.anthropic.com";

    private final RestClient http;
    Duration esperaEntreTentativas = Duration.ofSeconds(2);

    public AnthropicCliente(ObjectProvider<RestClient.Builder> builder) {
        this.http = builder.getIfAvailable(GeminiCliente::builderPadrao).clone().baseUrl(URL).build();
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public Resposta gerar(String prompt, String modelo, String chave) {
        var corpo = Map.of(
                "model", modelo,
                "max_tokens", 2000,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        JsonNode resposta = ChamadaComRetentativa.executar("Claude (Anthropic)", 4, esperaEntreTentativas, () -> http.post()
                .uri("/v1/messages")
                .header("x-api-key", chave)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));

        // content[] traz blocos; os de type "text" formam a resposta.
        String texto = StreamSupport.stream(resposta.path("content").spliterator(), false)
                .filter(b -> "text".equals(b.path("type").asString("")))
                .map(b -> b.path("text").asString(""))
                .reduce("", String::concat);
        if (texto.isBlank()) {
            throw new IaIndisponivelException("O Claude não retornou texto.");
        }
        return new Resposta(texto, resposta.path("model").asString(modelo));
    }
}
