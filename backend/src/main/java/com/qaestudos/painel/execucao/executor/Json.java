package com.qaestudos.painel.execucao.executor;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Pequenos utilitários para ler relatórios JSON das ferramentas com segurança. */
final class Json {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");

    private Json() {}

    /** Texto do nó, ou null se ausente/nulo (em vez de "" ou exceção). */
    static String texto(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asString(null);
    }

    static Long numero(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : Math.round(n.asDouble());
    }

    /** Lista do nó, aceitando array ou objeto (o k6 usa os dois formatos). */
    static List<JsonNode> itens(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? List.of() : List.copyOf(n.values());
    }

    /** Remove códigos de cor ANSI que algumas ferramentas embutem nas mensagens. */
    static String semCores(String s) {
        return s == null ? null : ANSI.matcher(s).replaceAll("");
    }
}
