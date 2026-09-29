package com.qaestudos.painel.projeto;

/**
 * Situação de um projeto na máquina.
 *
 * @param encontrado a pasta do projeto existe
 * @param instalado  as dependências da ferramenta estão instaladas
 *                   (node_modules ou, no k6, o executável)
 */
public record StatusProjeto(boolean encontrado, boolean instalado) {}
