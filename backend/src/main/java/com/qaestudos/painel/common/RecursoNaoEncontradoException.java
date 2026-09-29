package com.qaestudos.painel.common;

/**
 * Lançada quando algo pedido pela API não existe. O
 * {@link ApiExceptionHandler} converte em HTTP 404 — o Service só expressa
 * a regra ("não existe"), sem saber nada de HTTP.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
