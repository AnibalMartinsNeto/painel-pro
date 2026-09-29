package com.qaestudos.painel.projeto.dto;

import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.StatusProjeto;
import com.qaestudos.painel.projeto.TipoProjeto;

/**
 * DTO (Data Transfer Object): o formato exato do JSON devolvido em
 * {@code GET /api/projetos}. É o "contrato" da API com o front.
 *
 * <p>Repare que não expõe o caminho absoluto do diretório: detalhes
 * internos do servidor não precisam (nem devem) sair na API.
 */
public record ProjetoResumoResponse(
        String id,
        String nome,
        TipoProjeto tipo,
        boolean encontrado,
        boolean instalado) {

    public static ProjetoResumoResponse de(Projeto projeto, StatusProjeto status) {
        return new ProjetoResumoResponse(
                projeto.id(), projeto.nome(), projeto.tipo(), status.encontrado(), status.instalado());
    }
}
