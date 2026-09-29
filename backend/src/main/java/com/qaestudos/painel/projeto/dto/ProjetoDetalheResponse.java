package com.qaestudos.painel.projeto.dto;

import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ScriptExecucao;
import com.qaestudos.painel.projeto.StatusProjeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.util.List;

/**
 * JSON de {@code GET /api/projetos/{id}}: o resumo mais specs, navegadores,
 * scripts de execução do package.json e a URL da aplicação testada.
 */
public record ProjetoDetalheResponse(
        String id,
        String nome,
        TipoProjeto tipo,
        boolean encontrado,
        boolean instalado,
        String baseUrl,
        List<String> navegadores,
        List<String> specs,
        List<ScriptExecucao> scripts) {

    public static ProjetoDetalheResponse de(
            Projeto projeto, StatusProjeto status, String baseUrl, List<String> specs, List<ScriptExecucao> scripts) {
        return new ProjetoDetalheResponse(
                projeto.id(),
                projeto.nome(),
                projeto.tipo(),
                status.encontrado(),
                status.instalado(),
                baseUrl,
                projeto.navegadores(),
                specs,
                scripts);
    }
}
