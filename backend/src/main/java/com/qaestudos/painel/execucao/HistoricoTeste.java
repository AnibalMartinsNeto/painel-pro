package com.qaestudos.painel.execucao;

/**
 * Projeção: o histórico de um teste (identificado pela chave "spec › título")
 * em todas as execuções do projeto.
 */
public record HistoricoTeste(String chave, String spec, String titulo, long execucoes, long falhas, long aprovacoes) {

    /** Instável (flaky): já passou e já falhou — mesmo critério do painel Node. */
    public boolean instavel() {
        return falhas > 0 && aprovacoes > 0;
    }
}
