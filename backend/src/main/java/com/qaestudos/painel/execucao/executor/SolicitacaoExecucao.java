package com.qaestudos.painel.execucao.executor;

import java.util.List;

/**
 * O que o usuário pediu para executar (já validado pelo serviço).
 *
 * @param script    nome do script do package.json, ou null se for seleção manual
 * @param navegador navegador da ferramenta, ou null (k6)
 * @param dev       execução de teste: fora das métricas, relatórios e triagem
 */
public record SolicitacaoExecucao(
        String projetoId, String script, List<String> specs, String navegador, int retentativas, boolean abrirNavegador,
        boolean dev) {

    public SolicitacaoExecucao {
        specs = List.copyOf(specs);
    }

    /** Execução normal (conta nas métricas). */
    public SolicitacaoExecucao(String projetoId, String script, List<String> specs, String navegador, int retentativas,
                               boolean abrirNavegador) {
        this(projetoId, script, specs, navegador, retentativas, abrirNavegador, false);
    }
}
