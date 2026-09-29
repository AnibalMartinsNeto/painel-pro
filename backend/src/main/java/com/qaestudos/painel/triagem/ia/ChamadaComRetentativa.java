package com.qaestudos.painel.triagem.ia;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Executa uma chamada HTTP com NOVAS TENTATIVAS e espera crescente
 * ("backoff") quando o serviço está temporariamente indisponível:
 * 429 (muitas requisições) e 503 (sobrecarregado). Outros erros, como 401
 * (chave errada), não adianta repetir — falham na hora.
 *
 * <p>Cada tentativa falha e o erro final vão para o LOG: sem isso, um
 * serviço externo instável fica invisível para quem investiga o problema.
 */
final class ChamadaComRetentativa {

    private static final Logger log = LoggerFactory.getLogger(ChamadaComRetentativa.class);
    // As APIs de IA devolvem {"error":{"message":"..."}}: extrai só o texto.
    private static final Pattern MENSAGEM = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private ChamadaComRetentativa() {}

    static <T> T executar(String servico, int tentativas, Duration esperaBase, Supplier<T> chamada) {
        for (int i = 1; ; i++) {
            try {
                return chamada.get();
            } catch (HttpStatusCodeException e) {
                HttpStatusCode s = e.getStatusCode();
                boolean temporario = s.value() == 429 || s.value() == 503;
                if (!temporario || i == tentativas) {
                    log.warn("{} falhou com {} (tentativa {}/{}): {}", servico, s.value(), i, tentativas, mensagem(e));
                    throw new IaIndisponivelException("%s respondeu %d: %s".formatted(servico, s.value(), mensagem(e)));
                }
                log.warn("{} respondeu {} (tentativa {}/{}); tentando de novo em {}s", servico, s.value(), i, tentativas,
                        esperaBase.multipliedBy(i).toSeconds());
            } catch (ResourceAccessException e) {
                if (i == tentativas) {
                    log.warn("{} inacessível após {} tentativas: {}", servico, tentativas, e.getMessage());
                    throw new IaIndisponivelException("Não foi possível conectar ao %s: %s".formatted(servico, e.getMessage()));
                }
                log.warn("{} inacessível (tentativa {}/{}): {}", servico, i, tentativas, e.getMessage());
            }
            dormir(esperaBase.multipliedBy(i)); // 2s, 4s, 6s...
        }
    }

    /** Texto do erro para o usuário: o "message" do JSON da API, ou um trecho do corpo. */
    static String mensagem(HttpStatusCodeException e) {
        String corpo = e.getResponseBodyAsString();
        if (corpo.isBlank()) return e.getStatusText();
        Matcher m = MENSAGEM.matcher(corpo);
        return m.find() ? m.group(1).replace("\\\"", "\"") : corpo.substring(0, Math.min(300, corpo.length()));
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
