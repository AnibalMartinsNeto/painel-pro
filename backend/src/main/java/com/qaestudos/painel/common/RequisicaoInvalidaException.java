package com.qaestudos.painel.common;

/** Pedido que viola uma regra de negócio (ex.: spec que não existe no projeto) → HTTP 400. */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
