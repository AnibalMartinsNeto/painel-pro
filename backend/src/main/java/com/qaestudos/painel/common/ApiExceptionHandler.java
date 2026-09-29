package com.qaestudos.painel.common;

import com.qaestudos.painel.triagem.ia.IaIndisponivelException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento central de erros da API.
 *
 * <p>Em vez de cada Controller montar a própria resposta de erro, as
 * exceções sobem até aqui e viram um JSON padronizado no formato
 * "Problem Details" (RFC 9457): {@code type, title, status, detail}.
 * Para quem testa a API, isso significa erros sempre com a mesma forma.
 *
 * <pre>
 * RecursoNaoEncontradoException → 404  (não existe)
 * RequisicaoInvalidaException   → 400  (viola regra de negócio)
 * validação do corpo (@Valid)   → 400  (campos inválidos)
 * ConflitoException             → 409  (conflita com o estado atual)
 * </pre>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail invalida(RequisicaoInvalidaException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail camposInvalidos(MethodArgumentNotValidException ex) {
        String detalhe = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problema(HttpStatus.BAD_REQUEST, "Campos inválidos", detalhe);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, "JSON inválido", "O corpo da requisição não é um JSON válido para esta operação.");
    }

    /** 502 Bad Gateway: quem falhou foi um serviço externo do qual dependemos (a IA). */
    @ExceptionHandler(IaIndisponivelException.class)
    public ProblemDetail iaIndisponivel(IaIndisponivelException ex) {
        return problema(HttpStatus.BAD_GATEWAY, "Serviço de IA indisponível", ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setTitle(titulo);
        return p;
    }
}
