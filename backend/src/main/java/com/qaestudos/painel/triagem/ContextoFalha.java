package com.qaestudos.painel.triagem;

/** Tudo que a IA recebe sobre uma falha para escrever o bug. */
public record ContextoFalha(
        String ferramenta, String spec, String titulo, String mensagemErro, String tipoErro, String navegador, String codigoSpec) {}
