package com.qaestudos.painel.execucao;

/** Situação de uma execução. Gravado como texto na coluna execucao.status. */
public enum StatusExecucao {
    EM_ANDAMENTO,
    PASSOU,
    FALHOU,
    ERRO,
    CANCELADA;

    /** Execuções que chegaram ao fim e têm resultado de testes (entram nas métricas). */
    public boolean concluida() {
        return this == PASSOU || this == FALHOU;
    }
}
