package com.qaestudos.painel.projeto;

import com.qaestudos.painel.projeto.dto.ProjetoDetalheResponse;
import com.qaestudos.painel.projeto.dto.ProjetoResumoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Porta de entrada HTTP para projetos.
 *
 * <p>O papel do Controller é só traduzir: HTTP → chamada ao Service →
 * DTO → JSON. Nenhuma regra de negócio mora aqui. Se amanhã a mesma
 * funcionalidade for exposta por outro meio (fila, linha de comando), o
 * Service é reaproveitado inteiro.
 */
@RestController
@RequestMapping("/api/projetos")
public class ProjetoController {

    private final ProjetoService service;

    public ProjetoController(ProjetoService service) {
        this.service = service;
    }

    /** GET /api/projetos → lista resumida, com status de cada projeto. */
    @GetMapping
    public List<ProjetoResumoResponse> listar() {
        return service.listar().stream()
                .map(p -> ProjetoResumoResponse.de(p, service.status(p)))
                .toList();
    }

    /** GET /api/projetos/{id} → detalhe com specs; 404 se o id não existir. */
    @GetMapping("/{id}")
    public ProjetoDetalheResponse detalhar(@PathVariable String id) {
        Projeto projeto = service.buscar(id);
        return ProjetoDetalheResponse.de(projeto, service.status(projeto), service.listarSpecs(projeto));
    }
}
