package com.qaestudos.painel.execucao.executor;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifica a mensagem de erro de um teste (Cypress ou Playwright) numa
 * categoria legível — é o que permite ao QA ver de relance se a falha
 * parece do app (Asserção), do ambiente (Rede) ou do próprio teste
 * (Seletor, erro de código). A ORDEM das regras importa: a primeira que
 * casar vence.
 */
public final class ClassificadorErro {

    private record Regra(String categoria, Pattern padrao) {}

    private static final Regra[] REGRAS = {
        regra("Rede / Ambiente", "cy\\.visit\\(\\) failed|econnrefused|enotfound|etimedout|net::err_|network|cy\\.request\\(\\) failed"),
        regra("Exceção da aplicação", "uncaught exception|the following error originated from your application"),
        regra("Elemento / Seletor", "expected to find element|never found it|element.*detached|is not visible|covered by another|element\\(s\\) not found|intercepts pointer events"),
        regra("Timeout", "timed out|timeout \\d+ms exceeded|test timeout of"),
        regra("Erro no código do teste", "typeerror|referenceerror|syntaxerror|is not a function|is not defined"),
        regra("Asserção", "assertionerror|expected .* to |expect\\(.*\\)\\.|expected:"),
    };

    private ClassificadorErro() {}

    private static Regra regra(String categoria, String regex) {
        return new Regra(categoria, Pattern.compile(regex, Pattern.DOTALL));
    }

    public static String classificar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return null;
        }
        String m = mensagem.toLowerCase(Locale.ROOT);
        for (Regra r : REGRAS) {
            if (r.padrao().matcher(m).find()) {
                return r.categoria();
            }
        }
        return "Outro";
    }
}
