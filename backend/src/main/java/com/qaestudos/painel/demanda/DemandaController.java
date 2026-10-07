package com.qaestudos.painel.demanda;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API dos fluxos de demanda (análises feitas a partir de uma issue do Jira). */
@RestController
@RequestMapping("/api/demandas")
public class DemandaController {

    private final CoberturaDemandaService cobertura;
    private final ValidacaoDemandaService validacao;

    public DemandaController(CoberturaDemandaService cobertura, ValidacaoDemandaService validacao) {
        this.cobertura = cobertura;
        this.validacao = validacao;
    }

    /**
     * POST /api/demandas/DEV-1/validacao → rascunho do relatório de validação:
     * último resultado real de cada teste da demanda, veredito pelos fatos e o
     * texto (IA, ou direto dos resultados sem IA). Nada é publicado ainda.
     */
    @PostMapping("/{chave}/validacao")
    public ValidacaoDemandaService.Rascunho validacao(@PathVariable String chave) {
        return validacao.rascunho(chave);
    }

    /** POST /api/demandas/DEV-1/validacao/publicar → comenta o relatório revisado na demanda. */
    @PostMapping("/{chave}/validacao/publicar")
    public ValidacaoDemandaService.Publicado publicarValidacao(@PathVariable String chave,
                                                              @RequestBody ValidacaoDemandaService.Publicar relatorio) {
        return validacao.publicar(chave, relatorio);
    }

    /**
     * POST /api/demandas/DEV-1/cobertura?projeto=playwright → mapa de cobertura
     * da demanda (IA). POST, e não GET: chama a IA (custo/cota), então só roda
     * quando o QA pede, nunca num pré-carregamento do navegador.
     */
    @PostMapping("/{chave}/cobertura")
    public CoberturaDemandaService.Cobertura cobertura(@PathVariable String chave, @RequestParam String projeto) {
        return cobertura.mapear(projeto, chave);
    }
}
