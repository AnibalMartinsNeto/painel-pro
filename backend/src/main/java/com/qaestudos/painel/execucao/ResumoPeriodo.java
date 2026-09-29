package com.qaestudos.painel.execucao;

/**
 * Resultado de uma consulta agregada (projeção). Não é entidade: o JPQL
 * "select new ResumoPeriodo(...)" preenche direto este record.
 */
public record ResumoPeriodo(long execucoes, long testes, long aprovados, long reprovados) {

    /** Percentual de aprovação com 1 casa decimal, ou null sem testes avaliados. */
    public Double aprovacao() {
        long avaliados = aprovados + reprovados;
        return avaliados == 0 ? null : Math.round(aprovados * 1000.0 / avaliados) / 10.0;
    }
}
