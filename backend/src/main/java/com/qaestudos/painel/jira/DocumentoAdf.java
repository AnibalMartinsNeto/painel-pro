package com.qaestudos.painel.jira;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta descrições no ADF (Atlassian Document Format), o formato que a API
 * v3 do Jira Cloud exige: em vez de texto com marcação, a descrição é um
 * JSON com blocos tipados (parágrafo, título, lista, bloco de código...).
 *
 * <pre>
 * new DocumentoAdf().titulo("Passos").listaNumerada(List.of("a", "b")).montar()
 * </pre>
 */
public class DocumentoAdf {

    private final List<Map<String, Object>> blocos = new ArrayList<>();

    private static Map<String, Object> texto(String t) {
        return Map.of("type", "text", "text", t);
    }

    private static Map<String, Object> paragrafoDe(String t) {
        return Map.of("type", "paragraph", "content", List.of(texto(t)));
    }

    public DocumentoAdf paragrafo(String t) {
        if (t != null && !t.isBlank()) blocos.add(paragrafoDe(t));
        return this;
    }

    public DocumentoAdf titulo(String t) {
        blocos.add(Map.of("type", "heading", "attrs", Map.of("level", 3), "content", List.of(texto(t))));
        return this;
    }

    public DocumentoAdf listaNumerada(List<String> itens) {
        if (itens == null || itens.isEmpty()) return this;
        blocos.add(Map.of("type", "orderedList", "content",
                itens.stream().map(i -> Map.of("type", "listItem", "content", List.of(paragrafoDe(i)))).toList()));
        return this;
    }

    public DocumentoAdf codigo(String t) {
        if (t != null && !t.isBlank()) {
            blocos.add(Map.of("type", "codeBlock", "content", List.of(texto(t.length() > 4000 ? t.substring(0, 4000) + "\n[...]" : t))));
        }
        return this;
    }

    public Map<String, Object> montar() {
        return Map.of("type", "doc", "version", 1, "content", List.copyOf(blocos));
    }
}
