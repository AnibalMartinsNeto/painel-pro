package com.qaestudos.painel.triagem;

/** O que a falha é, na avaliação do QA (ou na sugestão da IA). */
public enum Classificacao {
    BUG_APLICACAO,   // defeito real no sistema testado
    FALHA_AUTOMACAO, // o problema está no teste (seletor, espera, código)
    AMBIENTE,        // rede, servidor fora, massa de dados
    INSTAVEL,        // flaky: às vezes passa, às vezes falha
    BUG_CONHECIDO    // já reportado, aguardando correção
}
