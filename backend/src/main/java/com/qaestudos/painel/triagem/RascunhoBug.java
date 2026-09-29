package com.qaestudos.painel.triagem;

import java.util.List;

/**
 * Rascunho de bug gerado pela IA (ou pela heurística, sem IA).
 *
 * @param origem "IA" ou "HEURISTICA"
 * @param modelo modelo de IA usado, ou null
 */
public record RascunhoBug(
        String titulo,
        Classificacao classificacao,
        Severidade severidade,
        String esperado,
        String encontrado,
        List<String> passos,
        String analise,
        String origem,
        String modelo) {

    public RascunhoBug {
        passos = passos == null ? List.of() : List.copyOf(passos);
    }
}
