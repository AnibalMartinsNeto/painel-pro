package com.qaestudos.painel.projeto;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Modelo de domínio: um projeto de testes que o painel sabe executar.
 *
 * <p>É a representação "interna" usada pelas regras de negócio. Ela não é
 * devolvida diretamente pela API — quem sai para o front é um DTO
 * (ver pacote {@code dto}). Assim dá para mudar o domínio sem quebrar o
 * contrato da API, e vice-versa.
 */
public record Projeto(
        String id,
        String nome,
        TipoProjeto tipo,
        Path diretorio,
        String pastaSpecs,
        Pattern padraoSpec,
        List<String> navegadores,
        Path arquivoRegras,
        List<Path> codigoSistema) {

    public Projeto {
        codigoSistema = codigoSistema == null ? List.of() : List.copyOf(codigoSistema);
    }

    /** Projeto sem arquivo de regras de negócio nem código do sistema. */
    public Projeto(String id, String nome, TipoProjeto tipo, Path diretorio, String pastaSpecs, Pattern padraoSpec,
                   List<String> navegadores) {
        this(id, nome, tipo, diretorio, pastaSpecs, padraoSpec, navegadores, null, List.of());
    }

    /** Projeto sem código do sistema. */
    public Projeto(String id, String nome, TipoProjeto tipo, Path diretorio, String pastaSpecs, Pattern padraoSpec,
                   List<String> navegadores, Path arquivoRegras) {
        this(id, nome, tipo, diretorio, pastaSpecs, padraoSpec, navegadores, arquivoRegras, List.of());
    }
}
