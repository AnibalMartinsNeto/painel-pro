package com.qaestudos.painel.triagem;

/** Tudo que a IA recebe sobre uma falha para escrever o bug. */
public record ContextoFalha(
        String ferramenta, String spec, String titulo, String mensagemErro, String tipoErro, String navegador, String codigoSpec,
        String regrasNegocio,
        String codigoSistema) {

    /** Falha sem trechos do código do sistema. */
    public ContextoFalha(String ferramenta, String spec, String titulo, String mensagemErro, String tipoErro, String navegador,
                         String codigoSpec, String regrasNegocio) {
        this(ferramenta, spec, titulo, mensagemErro, tipoErro, navegador, codigoSpec, regrasNegocio, null);
    }

    /** Falha sem arquivo de regras de negócio. */
    public ContextoFalha(String ferramenta, String spec, String titulo, String mensagemErro, String tipoErro, String navegador,
                         String codigoSpec) {
        this(ferramenta, spec, titulo, mensagemErro, tipoErro, navegador, codigoSpec, null, null);
    }
}
