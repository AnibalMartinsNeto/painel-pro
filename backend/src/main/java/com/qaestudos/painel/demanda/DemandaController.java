package com.qaestudos.painel.demanda;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API dos fluxos de demanda (análises feitas a partir de uma issue do Jira). */
@RestController
@RequestMapping("/api/demandas")
public class DemandaController {

    private final CoberturaDemandaService cobertura;

    public DemandaController(CoberturaDemandaService cobertura) {
        this.cobertura = cobertura;
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
