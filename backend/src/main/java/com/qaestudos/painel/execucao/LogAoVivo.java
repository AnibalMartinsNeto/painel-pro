package com.qaestudos.painel.execucao;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Saída de uma execução em andamento, transmitida em tempo real.
 *
 * <p>SSE (Server-Sent Events): o navegador abre UMA conexão HTTP e o
 * servidor vai empurrando eventos por ela ({@code event: linha}, ...),
 * sem o navegador precisar perguntar de novo. Quem conecta no meio da
 * execução recebe primeiro as linhas já emitidas e depois as novas.
 *
 * <p>Várias threads mexem aqui (a que lê o processo e as requisições dos
 * navegadores), por isso os métodos são {@code synchronized} e a lista de
 * assinantes é uma {@link CopyOnWriteArrayList}.
 */
public class LogAoVivo {

    private final List<String> linhas = new ArrayList<>();
    private final List<SseEmitter> assinantes = new CopyOnWriteArrayList<>();
    private String statusFinal;

    public synchronized void adicionar(String linha) {
        linhas.add(linha);
        for (SseEmitter e : assinantes) {
            enviar(e, "linha", linha);
        }
    }

    public synchronized SseEmitter assinar() {
        SseEmitter emitter = new SseEmitter(Duration.ofHours(2).toMillis());
        for (String l : linhas) {
            enviar(emitter, "linha", l);
        }
        if (statusFinal != null) {
            enviar(emitter, "fim", statusFinal);
            emitter.complete();
            return emitter;
        }
        assinantes.add(emitter);
        emitter.onCompletion(() -> assinantes.remove(emitter));
        emitter.onTimeout(() -> assinantes.remove(emitter));
        emitter.onError(t -> assinantes.remove(emitter));
        return emitter;
    }

    /** Avisa os assinantes que acabou (evento "fim" com o status) e fecha as conexões. */
    public synchronized void encerrar(StatusExecucao status) {
        statusFinal = status.name();
        for (SseEmitter e : assinantes) {
            enviar(e, "fim", statusFinal);
            e.complete();
        }
        assinantes.clear();
    }

    public synchronized String texto() {
        return String.join("\n", linhas);
    }

    private void enviar(SseEmitter emitter, String evento, String dado) {
        try {
            emitter.send(SseEmitter.event().name(evento).data(dado));
        } catch (IOException | IllegalStateException e) {
            // Navegador fechou a aba: só para de enviar para ele.
            assinantes.remove(emitter);
        }
    }
}
