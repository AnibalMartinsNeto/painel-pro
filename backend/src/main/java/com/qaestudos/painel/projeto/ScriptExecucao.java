package com.qaestudos.painel.projeto;

import java.util.List;

/**
 * Um script do package.json que executa testes (ex.: "test:diagnostics").
 * Vira um botão de execução rápida no painel.
 *
 * @param nome      nome do script ("test", "test:chrome"...)
 * @param comando   comando completo, como está no package.json
 * @param specs     specs que o script roda (todos, se não especificar)
 * @param navegador navegador fixado pelo script, ou null
 */
public record ScriptExecucao(String nome, String comando, List<String> specs, String navegador) {}
