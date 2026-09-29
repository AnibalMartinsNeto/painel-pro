package com.qaestudos.painel.projeto.dto;

import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.StatusProjeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.util.List;

/** JSON de {@code GET /api/projetos/{id}}: o resumo mais specs e navegadores. */
public record ProjetoDetalheResponse(
        String id,
        String nome,
        TipoProjeto tipo,
        boolean encontrado,
        boolean instalado,
        List<String> navegadores,
        List<String> specs) {

    public static ProjetoDetalheResponse de(Projeto projeto, StatusProjeto status, List<String> specs) {
        return new ProjetoDetalheResponse(
                projeto.id(),
                projeto.nome(),
                projeto.tipo(),
                status.encontrado(),
                status.instalado(),
                projeto.navegadores(),
                specs);
    }
}
