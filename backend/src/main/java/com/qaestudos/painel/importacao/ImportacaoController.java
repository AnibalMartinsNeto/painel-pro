package com.qaestudos.painel.importacao;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST (e não GET) porque altera dados no servidor. GET deve ser sempre
 * seguro de repetir e não ter efeitos colaterais — regra que também vale
 * na hora de testar uma API.
 */
@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

    private final ImportadorPainelNode importador;

    public ImportacaoController(ImportadorPainelNode importador) {
        this.importador = importador;
    }

    /** POST /api/importacoes/painel-node → importa o histórico do painel Node. */
    @PostMapping("/painel-node")
    public ImportadorPainelNode.Resultado importarPainelNode() {
        return importador.importar();
    }
}
