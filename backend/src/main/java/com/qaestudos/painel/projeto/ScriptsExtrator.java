package com.qaestudos.painel.projeto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta os comandos dos scripts do package.json e descobre quais
 * specs e qual navegador cada um usa. Cada ferramenta tem sua sintaxe:
 *
 * <pre>
 * Cypress:    cypress run --spec "a.cy.js,b.cy.js" --browser chrome
 * Playwright: playwright test tests/a.spec.js --project=chromium
 * k6:         k6 run tests/smoke.js &amp;&amp; k6 run tests/load.js
 * </pre>
 *
 * <p>Classe sem estado e sem dependências: lógica pura, fácil de testar.
 */
final class ScriptsExtrator {

    private static final Pattern CYPRESS_SPEC = Pattern.compile("--spec\\s+\"?([^\"\\s]+)\"?");
    private static final Pattern CYPRESS_BROWSER = Pattern.compile("--browser\\s+(\\S+)");
    private static final Pattern PW_PROJECT = Pattern.compile("--project[= ](\\S+)");
    private static final Pattern K6_RUN = Pattern.compile("k6 run\\s+(\\S+)");

    private ScriptsExtrator() {}

    static List<ScriptExecucao> extrair(TipoProjeto tipo, Map<String, String> scripts, List<String> specs) {
        List<ScriptExecucao> resultado = new ArrayList<>();
        new LinkedHashMap<>(scripts).forEach((nome, comando) -> {
            ScriptExecucao s = switch (tipo) {
                case CYPRESS -> cypress(nome, comando, specs);
                case PLAYWRIGHT -> playwright(nome, comando, specs);
                case K6 -> k6(nome, comando, specs);
            };
            if (s != null) {
                resultado.add(s);
            }
        });
        return resultado;
    }

    private static ScriptExecucao cypress(String nome, String comando, List<String> specs) {
        if (!comando.contains("cypress run")) {
            return null;
        }
        Matcher spec = CYPRESS_SPEC.matcher(comando);
        List<String> lista = spec.find() ? Arrays.stream(spec.group(1).split(",")).map(String::trim).toList() : specs;
        return new ScriptExecucao(nome, comando, lista, grupo(CYPRESS_BROWSER, comando));
    }

    private static ScriptExecucao playwright(String nome, String comando, List<String> specs) {
        int i = comando.indexOf("playwright test");
        if (i < 0 || comando.contains("--ui")) {
            return null;
        }
        List<String> arquivos = Arrays.stream(comando.substring(i + "playwright test".length()).trim().split("\\s+"))
                .filter(a -> !a.isBlank() && !a.startsWith("-"))
                .map(a -> a.replaceFirst("^\\./", ""))
                .toList();
        List<String> lista = arquivos.isEmpty()
                ? specs
                : specs.stream().filter(s -> arquivos.stream().anyMatch(a -> s.equals(a) || s.endsWith("/" + a))).toList();
        return new ScriptExecucao(nome, comando, lista, grupo(PW_PROJECT, comando));
    }

    private static ScriptExecucao k6(String nome, String comando, List<String> specs) {
        List<String> arquivos = new ArrayList<>();
        Matcher m = K6_RUN.matcher(comando);
        while (m.find()) {
            arquivos.add(m.group(1).replaceFirst("^\\./", ""));
        }
        if (arquivos.isEmpty()) {
            return null;
        }
        // Mantém a ordem do comando (smoke antes de carga, por exemplo).
        return new ScriptExecucao(nome, comando, arquivos.stream().filter(specs::contains).toList(), null);
    }

    private static String grupo(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1) : null;
    }
}
