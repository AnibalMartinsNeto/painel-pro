package com.qaestudos.painel.triagem.ia;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Google Gemini: POST /v1beta/models/{modelo}:generateContent, chave no cabeçalho x-goog-api-key. */
@Component
public class GeminiCliente implements ProvedorIa {

    static final String URL = "https://generativelanguage.googleapis.com";

    private final RestClient http;
    Duration esperaEntreTentativas = Duration.ofSeconds(2); // testes zeram para não esperar

    public GeminiCliente(ObjectProvider<RestClient.Builder> builder) {
        this.http = builder.getIfAvailable(GeminiCliente::builderPadrao).clone().baseUrl(URL).build();
    }

    static RestClient.Builder builderPadrao() {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(90)); // gerar texto pode demorar
        return RestClient.builder().requestFactory(fabrica);
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public Resposta gerar(String prompt, String modelo, String chave) {
        var corpo = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                // Pede JSON puro na resposta: facilita a leitura do rascunho.
                "generationConfig", Map.of("responseMimeType", "application/json", "maxOutputTokens", 4096));

        JsonNode resposta = ChamadaComRetentativa.executar("Gemini", 4, esperaEntreTentativas, () -> http.post()
                .uri("/v1beta/models/{modelo}:generateContent", modelo)
                .header("x-goog-api-key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));

        // candidates[0].content.parts[] — ignora as partes de "pensamento" do modelo.
        String texto = StreamSupport.stream(resposta.path("candidates").path(0).path("content").path("parts").spliterator(), false)
                .filter(p -> !p.path("thought").asBoolean(false))
                .map(p -> p.path("text").asString(""))
                .reduce("", String::concat);
        if (texto.isBlank()) {
            throw new IaIndisponivelException("O Gemini não retornou conteúdo (resposta vazia ou bloqueada).");
        }
        return new Resposta(texto, resposta.path("modelVersion").asString(modelo));
    }
}
