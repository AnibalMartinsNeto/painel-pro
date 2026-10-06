package com.qaestudos.painel.projeto.dto;

import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ScriptExecucao;
import com.qaestudos.painel.projeto.StatusProjeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.nio.file.Files;
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
        List<ScriptExecucao> scripts,
        RegrasNegocio regras,
        List<PastaSistema> codigoSistema) {

    /** Pasta com código do sistema testado que a IA consulta na triagem, e se ela existe. */
    public record PastaSistema(String pasta, boolean encontrada) {}

    /** O .md de regras de negócio que a IA usa na triagem: nome do arquivo e se ele existe. Null se não configurado. */
    public record RegrasNegocio(String arquivo, boolean encontrado) {}

    public static ProjetoDetalheResponse de(
            Projeto projeto, StatusProjeto status, String baseUrl, List<String> specs, List<ScriptExecucao> scripts,
            boolean regrasEncontradas) {
        return new ProjetoDetalheResponse(
                projeto.id(),
                projeto.nome(),
                projeto.tipo(),
                status.encontrado(),
                status.instalado(),
                baseUrl,
                projeto.navegadores(),
                specs,
                scripts,
                projeto.arquivoRegras() == null ? null
                        : new RegrasNegocio(projeto.arquivoRegras().getFileName().toString(), regrasEncontradas),
                projeto.codigoSistema().stream()
                        .map(p -> new PastaSistema(p.getFileName().toString(), Files.isDirectory(p)))
                        .toList());
    }
}
