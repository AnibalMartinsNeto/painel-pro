package com.qaestudos.painel.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento central de erros da API.
 *
 * <p>Em vez de cada Controller montar a própria resposta de erro, as
 * exceções sobem até aqui e viram um JSON padronizado no formato
 * "Problem Details" (RFC 9457): {@code type, title, status, detail}.
 * Para quem testa a API, isso significa erros sempre com a mesma forma.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Recurso não encontrado");
        return problema;
    }
}
