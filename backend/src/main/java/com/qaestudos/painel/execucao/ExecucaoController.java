package com.qaestudos.painel.execucao;

import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ExecucaoDetalheResponse;
import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ExecucaoResumoResponse;
import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ResumoProjetoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de leitura do histórico de execuções. */
@RestController
@RequestMapping("/api/execucoes")
public class ExecucaoController {

    private final ExecucaoService service;

    public ExecucaoController(ExecucaoService service) {
        this.service = service;
    }

    /** GET /api/execucoes?projeto=cypress → últimas 50 execuções do projeto. */
    @GetMapping
    public List<ExecucaoResumoResponse> listar(@RequestParam String projeto) {
        return service.listar(projeto).stream().map(ExecucaoResumoResponse::de).toList();
    }

    /** GET /api/execucoes/resumo?projeto=cypress → números da Visão geral. */
    @GetMapping("/resumo")
    public ResumoProjetoResponse resumo(@RequestParam String projeto) {
        return ResumoProjetoResponse.de(service.resumir(projeto));
    }

    /** GET /api/execucoes/42 → execução com todos os resultados de teste. */
    @GetMapping("/{id}")
    public ExecucaoDetalheResponse detalhar(@PathVariable Long id) {
        return ExecucaoDetalheResponse.de(service.buscar(id));
    }
}
