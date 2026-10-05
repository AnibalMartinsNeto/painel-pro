package com.qaestudos.painel.relatorio;

import com.qaestudos.painel.execucao.dto.ExecucaoDtos.ExecucaoResumoResponse;
import com.qaestudos.painel.relatorio.RelatorioService.Relatorio;
import com.qaestudos.painel.relatorio.RelatorioService.TesteComFalha;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API da tela Relatórios: números de todo o histórico e exportação CSV. */
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    public record RelatorioResponse(
            long execucoes, long testesUnicos, long jaFalharam, long instaveis, long tempoTotalMs,
            List<ExecucaoResumoResponse> aprovacaoPorExecucao, List<TesteComFalha> testesComFalha) {

        static RelatorioResponse de(Relatorio r) {
            return new RelatorioResponse(r.execucoes(), r.testesUnicos(), r.jaFalharam(), r.instaveis(), r.tempoTotalMs(),
                    r.aprovacaoPorExecucao().stream().map(ExecucaoResumoResponse::de).toList(), r.testesComFalha());
        }
    }

    /** GET /api/relatorios?projeto=cypress → números do histórico completo do projeto. */
    @GetMapping
    public RelatorioResponse gerar(@RequestParam String projeto) {
        return RelatorioResponse.de(service.gerar(projeto));
    }

    /**
     * GET /api/relatorios/execucoes.csv?projeto=cypress → download do CSV.
     * O cabeçalho Content-Disposition: attachment faz o navegador baixar o
     * arquivo (com este nome) em vez de mostrá-lo na tela.
     */
    @GetMapping("/execucoes.csv")
    public ResponseEntity<String> exportarCsv(@RequestParam String projeto) {
        String nome = "qa-panel-%s-execucoes-%s.csv".formatted(projeto, LocalDate.now());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(service.exportarCsv(projeto));
    }
}
