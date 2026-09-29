package com.qaestudos.painel.execucao.executor;

import java.util.List;

/**
 * O que o usuário pediu para executar (já validado pelo serviço).
 *
 * @param script    nome do script do package.json, ou null se for seleção manual
 * @param navegador navegador da ferramenta, ou null (k6)
 */
public record SolicitacaoExecucao(
        String projetoId, String script, List<String> specs, String navegador, int retentativas, boolean abrirNavegador) {

    public SolicitacaoExecucao {
        specs = List.copyOf(specs);
    }
}
