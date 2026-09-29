package com.qaestudos.painel.triagem.ia;

/**
 * O serviço de IA externo falhou (fora do ar, sobrecarregado, chave
 * inválida). Vira HTTP 502 Bad Gateway: o erro não é do nosso servidor nem
 * do pedido do usuário, e sim de um serviço do qual dependemos.
 */
public class IaIndisponivelException extends RuntimeException {

    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
