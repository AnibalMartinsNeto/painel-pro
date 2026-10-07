package com.qaestudos.painel.execucao;

import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ExecucaoDetalheResponse;
import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ExecucaoResumoResponse;
import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ResumoProjetoResponse;
import com.qaestudos.painel.execucao.dto.NovaExecucaoRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** API de execuções: histórico, resumo, disparo, cancelamento e log ao vivo. */
@RestController
@RequestMapping("/api/execucoes")
public class ExecucaoController {

    private final ExecucaoService service;
    private final OrquestradorExecucao orquestrador;

    public ExecucaoController(ExecucaoService service, OrquestradorExecucao orquestrador) {
        this.service = service;
        this.orquestrador = orquestrador;
    }

    /** GET /api/execucoes?projeto=cypress → últimas 50 execuções do projeto. */
    @GetMapping
    public List<ExecucaoResumoResponse> listar(@RequestParam String projeto) {
        return service.listar(projeto).stream().map(ExecucaoResumoResponse::de).toList();
    }

    /**
     * POST /api/execucoes → dispara uma execução.
     * 202 ACCEPTED (e não 200): o pedido foi aceito, mas o trabalho continua
     * em segundo plano. O corpo traz o id para acompanhar pelo log ao vivo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecucaoResumoResponse iniciar(@Valid @RequestBody NovaExecucaoRequest pedido) {
        return ExecucaoResumoResponse.de(orquestrador.iniciar(pedido.paraSolicitacao()));
    }

    /** GET /api/execucoes/em-andamento → a execução rodando agora, ou 204 (sem conteúdo). */
    @GetMapping("/em-andamento")
    public ResponseEntity<ExecucaoResumoResponse> emAndamento() {
        return orquestrador.emAndamento()
                .map(id -> ResponseEntity.ok(ExecucaoResumoResponse.de(service.buscar(id))))
                .orElse(ResponseEntity.noContent().build());
    }

    /** POST /api/execucoes/{id}/cancelar → interrompe os processos da execução. */
    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancelar(@PathVariable Long id) {
        orquestrador.cancelar(id);
    }

    /** GET /api/execucoes/{id}/log → fluxo SSE com a saída ao vivo. */
    @GetMapping(path = "/{id}/log", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter logAoVivo(@PathVariable Long id) {
        return orquestrador.assinarLog(id);
    }

    /** GET /api/execucoes/{id}/log.txt → log completo gravado no banco. */
    @GetMapping(path = "/{id}/log.txt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String logGravado(@PathVariable Long id) {
        String log = service.buscar(id).getLog();
        return log == null ? "(esta execução não tem log gravado)" : log;
    }

    /** GET /api/execucoes/resumo?projeto=cypress → números da Visão geral. */
    @GetMapping("/resumo")
    public ResumoProjetoResponse resumo(@RequestParam String projeto) {
        return ResumoProjetoResponse.de(service.resumir(projeto));
    }

    /** GET /api/execucoes/duracoes?projeto=cypress → base da previsão de tempo (médias das execuções reais). */
    @GetMapping("/duracoes")
    public ExecucaoService.Duracoes duracoes(@RequestParam String projeto) {
        return service.duracoes(projeto);
    }

    /** GET /api/execucoes/42 → execução com todos os resultados de teste. */
    @GetMapping("/{id}")
    public ExecucaoDetalheResponse detalhar(@PathVariable Long id) {
        return ExecucaoDetalheResponse.de(service.buscar(id), service.evidencias(id));
    }
}
