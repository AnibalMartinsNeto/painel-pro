package com.qaestudos.painel.jira;

import com.qaestudos.painel.triagem.Triagem;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API da tela Jira: busca de demanda e lista de bugs publicados. */
@RestController
@RequestMapping("/api/jira")
public class JiraController {

    private final PublicacaoJiraService service;

    public JiraController(PublicacaoJiraService service) {
        this.service = service;
    }

    public record BugPublicado(String chave, String url, String titulo, String chaveTeste, String demanda, Instant publicadaEm) {
        static BugPublicado de(Triagem t) {
            return new BugPublicado(t.getJiraIssue(), t.getJiraUrl(), t.getTitulo(), t.getChaveTeste(), t.getDemanda(), t.getPublicadaEm());
        }
    }

    /** GET /api/jira/demandas/DEV-1?projeto=cypress → a issue no Jira e os specs que a citam. */
    @GetMapping("/demandas/{chave}")
    public PublicacaoJiraService.Demanda demanda(@PathVariable String chave, @RequestParam String projeto) {
        return service.buscarDemanda(projeto, chave);
    }

    /** GET /api/jira/issues?maximo=50 → últimas issues do projeto no Jira (consulta o Jira na hora). */
    @GetMapping("/issues")
    public List<JiraCliente.IssueHistorico> historico(@RequestParam(defaultValue = "50") int maximo) {
        return service.historico(maximo);
    }

    /** GET /api/jira/bugs?projeto=cypress → bugs que o painel publicou no Jira. */
    @GetMapping("/bugs")
    public List<BugPublicado> bugs(@RequestParam String projeto) {
        return service.listarPublicados(projeto).stream().map(BugPublicado::de).toList();
    }
}
