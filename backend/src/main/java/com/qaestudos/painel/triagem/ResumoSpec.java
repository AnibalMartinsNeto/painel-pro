package com.qaestudos.painel.triagem;

import java.util.regex.Pattern;

/**
 * O código do spec que vai para a IA, sem desperdiçar o limite do prompt.
 *
 * <p>Spec pequeno vai inteiro. Spec grande vai em duas partes:
 * <ol>
 *   <li>o <b>bloco completo do teste que falhou</b> (é o que importa);
 *   <li>um <b>resumo do resto</b>: estrutura ({@code describe}/{@code it}),
 *       hooks ({@code beforeEach}...), asserções e chamadas de Page Object e
 *       de API — o que diz O QUE o spec verifica, sem o código todo.
 * </ol>
 * Linhas numeradas como no arquivo, para a IA poder citar a linha.
 */
public final class ResumoSpec {

    /** Até este tamanho, o spec vai inteiro: o contexto completo vale mais que o resumo. */
    static final int INTEIRO_ATE = 6_000;

    private static final Pattern RELEVANTE = Pattern.compile(
            "\\b(?:describe|context|it|test|before|beforeEach|after|afterEach)(?:\\.\\w+)?\\s*\\("   // estrutura e hooks
            + "|\\bexpect\\s*\\(|\\.should\\s*\\(|\\bassert\\b"                                       // asserções
            + "|\\b\\w+Page\\.\\w+\\s*\\("                                                            // Page Objects
            + "|\\bcy\\.(?:request|api|intercept|visit|apiLogin|api\\w+)\\b|\\bpage\\.(?:goto|route)\\b|\\bapi\\.\\w+\\s*\\("
            // k6: grupos, checks (cada "nome": (r) => ... é uma verificação), chamadas HTTP e limites
            + "|\\b(?:group|check)\\s*\\(|[\"'][^\"']+[\"']\\s*:\\s*\\(\\w+\\)\\s*=>|\\bhttp\\.(?:get|post|put|patch|del|request)\\s*\\("
            + "|\\bthresholds\\b|p\\(\\d+\\)\\s*<|rate\\s*[<>=]|\\bnew\\s+(?:Trend|Rate|Counter)\\s*\\(|\\bwaitFor\\s*\\(");

    private ResumoSpec() {}

    public static String paraPrompt(String codigoSpec, String tituloTeste) {
        if (codigoSpec == null || codigoSpec.isBlank()) return null;
        if (codigoSpec.length() <= INTEIRO_ATE) return codigoSpec;

        String[] linhas = codigoSpec.split("\\R", -1);
        String bloco = BuscadorCodigo.blocoDoTeste(codigoSpec, tituloTeste);
        int inicioBloco = bloco == codigoSpec ? -1 : indiceDoBloco(linhas, bloco);
        int fimBloco = inicioBloco < 0 ? -1 : inicioBloco + bloco.split("\\R", -1).length - 2;

        StringBuilder sb = new StringBuilder();
        if (inicioBloco >= 0) {
            sb.append("Teste que falhou (bloco completo):\n");
            for (int i = inicioBloco; i <= fimBloco && i < linhas.length; i++) linha(sb, i, linhas[i]);
            sb.append("\nResumo do restante do spec (estrutura, hooks, asserções, Page Objects, API):\n");
        } else {
            sb.append("Resumo do spec (estrutura, hooks, asserções, Page Objects, API):\n");
        }
        for (int i = 0; i < linhas.length; i++) {
            if (i >= inicioBloco && i <= fimBloco) continue;
            if (RELEVANTE.matcher(linhas[i]).find()) linha(sb, i, linhas[i]);
        }
        return sb.toString().stripTrailing();
    }

    /** Até quanto o resumo de UM spec ocupa (o mapa de cobertura manda vários). */
    static final int LIMITE_ESTRUTURA = 2_500;

    /**
     * Só a estrutura do spec (describe/it, hooks, asserções, Page Objects, API),
     * numerada: diz O QUE o spec verifica sem mandar o arquivo inteiro.
     */
    public static String estrutura(String codigoSpec) {
        if (codigoSpec == null || codigoSpec.isBlank()) return "(spec vazio)";
        String[] linhas = codigoSpec.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linhas.length && sb.length() < LIMITE_ESTRUTURA; i++) {
            if (RELEVANTE.matcher(linhas[i]).find()) linha(sb, i, linhas[i].strip());
        }
        String s = sb.toString().stripTrailing();
        return sb.length() >= LIMITE_ESTRUTURA ? s + "\n   [... resumo cortado ...]" : s;
    }

    private static int indiceDoBloco(String[] linhas, String bloco) {
        String primeira = bloco.split("\\R", 2)[0];
        for (int i = 0; i < linhas.length; i++) if (linhas[i].equals(primeira)) return i;
        return -1;
    }

    private static void linha(StringBuilder sb, int indice, String texto) {
        sb.append(String.format("%4d| ", indice + 1)).append(texto).append('\n');
    }
}
