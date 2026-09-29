package com.qaestudos.painel.triagem.ia;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Executa uma chamada HTTP com NOVAS TENTATIVAS e espera crescente
 * ("backoff") quando o serviço está temporariamente indisponível:
 * 429 (muitas requisições) e 503 (sobrecarregado). Outros erros, como 401
 * (chave errada), não adianta repetir — falham na hora.
 */
final class ChamadaComRetentativa {

    private ChamadaComRetentativa() {}

    static <T> T executar(String servico, int tentativas, Duration esperaBase, Supplier<T> chamada) {
        for (int i = 1; ; i++) {
            try {
                return chamada.get();
            } catch (HttpStatusCodeException e) {
                HttpStatusCode s = e.getStatusCode();
                boolean temporario = s.value() == 429 || s.value() == 503;
                if (!temporario || i == tentativas) {
                    throw new IaIndisponivelException("%s respondeu %d: %s".formatted(servico, s.value(), mensagem(e)));
                }
            } catch (ResourceAccessException e) {
                if (i == tentativas) {
                    throw new IaIndisponivelException("Não foi possível conectar ao %s: %s".formatted(servico, e.getMessage()));
                }
            }
            dormir(esperaBase.multipliedBy(i)); // 2s, 4s, 6s...
        }
    }

    private static String mensagem(HttpStatusCodeException e) {
        String corpo = e.getResponseBodyAsString();
        // As APIs devolvem {"error":{"message":"..."}}; mostra só um trecho.
        return corpo.isBlank() ? e.getStatusText() : corpo.substring(0, Math.min(300, corpo.length()));
    }

    private static void dormir(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IaIndisponivelException("Chamada à IA interrompida.");
        }
    }
}
