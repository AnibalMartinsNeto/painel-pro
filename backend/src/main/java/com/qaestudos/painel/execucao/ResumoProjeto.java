package com.qaestudos.painel.execucao;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Resumo de um projeto para a Visão geral (modelo de domínio; o Controller
 * converte em DTO).
 *
 * @param ultimaExecucao    última execução concluída, ou null
 * @param ultimaPorScript   nome do script → última execução dele
 */
public record ResumoProjeto(
        YearMonth mes,
        ResumoPeriodo periodo,
        List<FalhasModulo> falhasPorModulo,
        Execucao ultimaExecucao,
        Map<String, Execucao> ultimaPorScript) {

    public record FalhasModulo(String modulo, long falhas, long percentual) {}
}
