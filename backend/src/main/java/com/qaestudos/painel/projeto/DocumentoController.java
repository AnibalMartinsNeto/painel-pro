package com.qaestudos.painel.projeto;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API da aba Documentação: os .md do projeto de testes. */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    /** GET /api/documentos?projeto=playwright → os documentos encontrados. */
    @GetMapping
    public List<DocumentoService.Documento> listar(@RequestParam String projeto) {
        return service.listar(projeto);
    }

    /** GET /api/documentos/conteudo?projeto=playwright&id=serverest-qa/README.md → o Markdown cru. */
    @GetMapping(path = "/conteudo", produces = "text/markdown;charset=UTF-8")
    public String conteudo(@RequestParam String projeto, @RequestParam String id) {
        return service.ler(projeto, id);
    }
}
