package com.qaestudos.painel.triagem;

import com.qaestudos.painel.jira.PublicacaoJiraService;
import com.qaestudos.painel.triagem.TriagemRepository.FalhaEmAberto;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API da triagem: fila de falhas, análise com IA e revisão do QA. */
@RestController
@RequestMapping("/api/triagem")
public class TriagemController {

    private final TriagemService service;
    private final PublicacaoJiraService publicacao;

    public TriagemController(TriagemService service, PublicacaoJiraService publicacao) {
        this.service = service;
        this.publicacao = publicacao;
    }

    public record TriagemResponse(
            Classificacao classificacao, Classificacao classificacaoSugerida, Severidade severidade,
            String titulo, String esperado, String encontrado, List<String> passos, String analise,
            String origemRascunho, String modelo, String observacoes, Instant atualizadaEm,
            String jiraIssue, String jiraUrl, String demanda) {

        static TriagemResponse de(Triagem t) {
            return t == null ? null : new TriagemResponse(t.getClassificacao(), t.getClassificacaoSugerida(), t.getSeveridade(),
                    t.getTitulo(), t.getEsperado(), t.getEncontrado(), t.getPassos(), t.getAnalise(),
                    t.getOrigemRascunho(), t.getModelo(), t.getObservacoes(), t.getAtualizadaEm(),
                    t.getJiraIssue(), t.getJiraUrl(), t.getDemanda());
        }
    }

    public record PublicarRequest(String demanda) {}

    /**
     * POST /api/triagem/{resultadoId}/publicar → cria o bug no Jira a partir da
     * triagem salva e o liga à demanda informada (opcional). 409 se já publicado.
     */
    @PostMapping("/{resultadoId}/publicar")
    public PublicacaoJiraService.Publicacao publicar(@PathVariable Long resultadoId, @RequestBody(required = false) PublicarRequest r) {
        return publicacao.publicar(resultadoId, r == null ? null : r.demanda());
    }

    public record FalhaResponse(
            Long resultadoId, Long execucaoId, String spec, String titulo, String chave, String mensagemErro,
            String tipoErro, Instant ocorridaEm, String navegador, TriagemResponse triagem) {

        static FalhaResponse de(FalhaEmAberto f, Triagem t) {
            return new FalhaResponse(f.getResultadoId(), f.getExecucaoId(), f.getSpec(), f.getTitulo(), f.getChave(),
                    f.getMensagemErro(), f.getTipoErro(), f.getOcorridaEm(), f.getNavegador(), TriagemResponse.de(t));
        }
    }

    public record RevisaoRequest(Classificacao classificacao, Severidade severidade, String titulo, String esperado,
                                 String encontrado, List<String> passos, String observacoes) {}

    /** GET /api/triagem?projeto=cypress → testes que falham hoje, com a triagem de cada um. */
    @GetMapping
    public List<FalhaResponse> listar(@RequestParam String projeto) {
        return service.listar(projeto).stream().map(i -> FalhaResponse.de(i.falha(), i.triagem())).toList();
    }

    /** POST /api/triagem/{resultadoId}/analisar → gera o rascunho do bug com IA (ou heurística). */
    @PostMapping("/{resultadoId}/analisar")
    public TriagemResponse analisar(@PathVariable Long resultadoId) {
        return TriagemResponse.de(service.analisar(resultadoId));
    }

    /** PUT /api/triagem/{resultadoId} → grava a revisão/classificação do QA. */
    @PutMapping("/{resultadoId}")
    public TriagemResponse salvar(@PathVariable Long resultadoId, @RequestBody RevisaoRequest r) {
        return TriagemResponse.de(service.salvar(resultadoId, new TriagemService.Revisao(
                r.classificacao(), r.severidade(), r.titulo(), r.esperado(), r.encontrado(), r.passos(), r.observacoes())));
    }
}
