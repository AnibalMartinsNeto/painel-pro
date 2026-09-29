package com.qaestudos.painel.common;

/** Pedido válido, mas incompatível com o estado atual (ex.: já há execução rodando) → HTTP 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
