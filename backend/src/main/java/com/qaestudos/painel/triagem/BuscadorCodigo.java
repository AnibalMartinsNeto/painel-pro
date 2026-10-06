package com.qaestudos.painel.triagem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Acha, no código-fonte do SISTEMA TESTADO, os trechos ligados a uma falha,
 * do jeito que um QA faria na mão:
 *
 * <ol>
 *   <li>lê o spec e os arquivos que ele importa (Page Objects, fixtures) e
 *       extrai <b>termos</b>: rotas ({@code /admin/listarusuarios}),
 *       {@code data-testid} e textos da tela/erro;
 *   <li>procura os termos nos arquivos do sistema e pontua cada arquivo;
 *   <li><b>segue a rota até o componente</b>: a linha
 *       {@code <Route path="/admin/listarusuarios" component={ ShowUsers } />}
 *       mais o {@code import ShowUsers from './views/admin/showUsers'} levam
 *       ao arquivo da tela, mesmo que ele não cite nenhum termo;
 *   <li>devolve só os trechos relevantes, com número de linha, dentro de um
 *       limite de tamanho (o prompt da IA não pode crescer sem fim).
 * </ol>
 *
 * <p>Classe pura (sem Spring nem banco): testável com pastas temporárias.
 */
public final class BuscadorCodigo {

    static final int LIMITE_TOTAL = 14_000;     // caracteres de código no prompt
    static final int LIMITE_POR_ARQUIVO = 4_000;
    static final int MAX_ARQUIVOS = 4;
    private static final int CONTEXTO = 8;       // linhas antes/depois de cada ocorrência
    private static final int MAX_TERMOS = 40;
    private static final long TAMANHO_MAXIMO_ARQUIVO = 200_000;

    private static final Set<String> PASTAS_IGNORADAS = Set.of(
            "node_modules", ".git", "build", "dist", "coverage", "test", "tests", "__tests__", "docs", "public", "target");
    private static final Pattern EXTENSOES = Pattern.compile(".*\\.(js|jsx|ts|tsx|mjs|cjs)$");
    private static final Pattern ARQUIVO_DE_TESTE = Pattern.compile(".*\\.(test|spec|cy)\\.[jt]sx?$|.*\\.min\\.js$");

    private static final Pattern IMPORT_RELATIVO = Pattern.compile("(?:require\\(|from\\s+)[\"'](\\.{1,2}/[^\"']+)[\"']");
    private static final Pattern ROTA = Pattern.compile("[\"'`](/[a-zA-Z][\\w\\-]*(?:/[\\w\\-]+)*)");
    private static final Pattern TESTID = Pattern.compile("(?:getByTestId\\(\\s*|data-testid=\\\\?[\"']?)[\"']?([\\w\\-]{3,})");
    private static final Pattern TEXTO = Pattern.compile("[\"'`]([^\"'`\\n]{6,60})[\"'`]");
    private static final Pattern TITULO_DE_TESTE = Pattern.compile("\\b(?:describe|context|it|test)(?:\\.\\w+)?\\(\\s*[\"'`]([^\"'`\\n]+)[\"'`]");
    private static final Pattern INICIO_DE_TESTE = Pattern.compile("\\b(?:it|test)(?:\\.\\w+)?\\(");
    private static final Pattern CHAMADA_DE_METODO = Pattern.compile("\\.(\\w+)\\(");
    private static final Pattern DEFINICAO_DE_METODO = Pattern.compile("^\\s*(?:async\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern COMPONENTE_DA_ROTA = Pattern.compile("(?:component=\\{\\s*|element=\\{\\s*<)(\\w+)");

    private BuscadorCodigo() {}

    /** Um trecho do sistema: caminho para exibir (pasta/arquivo) e as linhas numeradas. */
    public record Trecho(String arquivo, String conteudo) {}

    /** Resultado da busca: os termos usados e os trechos achados (vazio se nada bateu). */
    public record Resultado(List<String> termos, List<Trecho> trechos) {

        public boolean vazio() {
            return trechos.isEmpty();
        }

        /** Texto pronto para o prompt. */
        public String formatar() {
            StringBuilder sb = new StringBuilder();
            for (Trecho t : trechos) {
                sb.append("Arquivo: ").append(t.arquivo()).append('\n').append(t.conteudo()).append("\n\n");
            }
            return sb.toString().strip();
        }
    }

    /**
     * @param pastaProjeto pasta do projeto de teste (para seguir os imports do spec)
     * @param spec         caminho do spec relativo à pasta do projeto
     * @param codigoSpec   conteúdo do spec (pode ser null)
     * @param tituloTeste  título do teste que falhou ("describe › it"), para recortar o bloco dele no spec
     * @param mensagemErro mensagem de erro do teste
     * @param pastasSistema pastas com o código do sistema testado
     */
    public static Resultado buscar(Path pastaProjeto, String spec, String codigoSpec, String tituloTeste, String mensagemErro,
                                   List<Path> pastasSistema) {
        List<Path> pastas = pastasSistema.stream().filter(Files::isDirectory).toList();
        if (pastas.isEmpty() || codigoSpec == null) return new Resultado(List.of(), List.of());

        Termos termos = extrairTermos(pastaProjeto, pastaProjeto.resolve(spec), codigoSpec, blocoDoTeste(codigoSpec, tituloTeste), mensagemErro);
        if (termos.todos().isEmpty()) return new Resultado(List.of(), List.of());

        // 1. Pontua cada arquivo do sistema pelos termos que ele contém.
        Map<Path, Arquivo> arquivos = new LinkedHashMap<>();
        for (Path pasta : pastas) {
            for (Path f : listarCodigo(pasta)) {
                String conteudo = ler(f);
                if (conteudo == null) continue;
                Arquivo a = new Arquivo(pasta, f, conteudo.split("\\R", -1));
                pontuar(a, termos);
                arquivos.put(f, a);
            }
        }

        // 2. Segue as rotas até os componentes (Route path=... component={X} + import X from './...').
        for (Arquivo a : List.copyOf(arquivos.values())) {
            for (int i = 0; i < a.linhas.length; i++) {
                String linha = a.linhas[i];
                if (termos.rotas.stream().noneMatch(r -> linha.contains("\"" + r + "\"") || linha.contains("'" + r + "'"))) continue;
                Matcher m = COMPONENTE_DA_ROTA.matcher(linha);
                if (!m.find()) continue;
                Path alvo = resolverImport(a, m.group(1));
                Arquivo componente = alvo == null ? null : arquivos.get(alvo);
                if (componente != null) {
                    // A tela da rota vem antes dos arquivos que só citam termos genéricos, e a rota que
                    // o teste VISITA (spec → método do Page Object → goto) vem antes das outras.
                    boolean visitada = termos.rotasVisitadas.stream().anyMatch(r -> linha.contains("\"" + r + "\"") || linha.contains("'" + r + "'"));
                    componente.pontos += visitada ? 200 : 50;
                    componente.inteiro = true; // a tela da rota: vai inteira (dentro do limite)
                }
            }
        }

        // 3. Os mais relevantes, com os trechos em volta das ocorrências.
        List<Arquivo> escolhidos = arquivos.values().stream()
                .filter(a -> a.pontos > 0)
                .sorted(Comparator.comparingInt((Arquivo a) -> a.pontos).reversed())
                .limit(MAX_ARQUIVOS)
                .toList();
        List<Trecho> trechos = new ArrayList<>();
        int total = 0;
        for (Arquivo a : escolhidos) {
            String conteudo = a.trecho();
            if (total + conteudo.length() > LIMITE_TOTAL) break;
            total += conteudo.length();
            trechos.add(new Trecho(a.nomeExibido(), conteudo));
        }
        return new Resultado(termos.todos(), trechos);
    }

    // ------------------------------------------------------------- termos

    /** @param rotasVisitadas rotas que o teste de fato abre (no spec ou nos métodos do Page Object que ele chama) */
    record Termos(Set<String> rotas, Set<String> rotasVisitadas, Set<String> testIds, Set<String> textos) {
        List<String> todos() {
            List<String> t = new ArrayList<>(rotas);
            t.addAll(testIds);
            t.addAll(textos);
            return t.stream().limit(MAX_TERMOS).toList();
        }
    }

    /**
     * Rotas e data-testid do spec e dos Page Objects que ELE usa; textos só do
     * spec e do erro (os mais específicos), sem os títulos dos testes.
     *
     * <p>Os imports são seguidos (o spec Playwright chega aos Page Objects pelas
     * fixtures), mas um arquivo importado só contribui com termos se o nome dele
     * aparece no spec: {@code adminUsuariosPage} → {@code AdminUsuariosPage.js}.
     * Sem esse filtro, as fixtures trariam os termos de TODAS as telas.
     */
    static Termos extrairTermos(Path pastaProjeto, Path arquivoSpec, String codigoSpec, String blocoDoTeste, String mensagemErro) {
        Set<String> rotas = new LinkedHashSet<>();
        Set<String> testIds = new LinkedHashSet<>();
        Set<String> textos = new LinkedHashSet<>();
        Set<String> rotasVisitadas = new LinkedHashSet<>();
        coletar(ROTA, blocoDoTeste, rotasVisitadas);
        Set<String> chamadas = new LinkedHashSet<>();
        coletar(CHAMADA_DE_METODO, blocoDoTeste, chamadas);

        Map<Path, String> codigos = new LinkedHashMap<>();
        codigos.put(arquivoSpec.normalize(), codigoSpec);
        seguirImports(pastaProjeto, arquivoSpec, codigoSpec, codigos, 0);
        String specMinusculo = codigoSpec.toLowerCase();
        codigos.forEach((arquivo, codigo) -> {
            String nome = arquivo.getFileName().toString().replaceFirst("\\.[^.]+$", "").toLowerCase();
            if (arquivo.equals(arquivoSpec.normalize()) || (nome.length() >= 4 && specMinusculo.contains(nome))) {
                coletar(ROTA, codigo, rotas);
                coletar(TESTID, codigo, testIds);
                if (!arquivo.equals(arquivoSpec.normalize())) rotasVisitadas.addAll(rotasDosMetodos(codigo, chamadas));
            }
        });
        Set<String> titulos = new LinkedHashSet<>();
        coletar(TITULO_DE_TESTE, codigoSpec, titulos);
        rotas.removeIf(r -> r.length() < 3 || r.startsWith("//"));

        for (String fonte : new String[] {codigoSpec, mensagemErro}) {
            if (fonte == null) continue;
            Matcher m = TEXTO.matcher(fonte);
            while (m.find()) {
                String t = m.group(1).strip();
                // Texto "de tela": tem espaço e letras; não é seletor, caminho, URL nem código.
                if (!titulos.contains(t) && t.contains(" ") && t.matches(".*\\p{L}{3,}.*") && !t.matches(".*[{}()=<>;\\[\\]$].*")
                        && !t.startsWith("/") && !t.startsWith("http") && !t.startsWith(".")) {
                    textos.add(t);
                }
            }
        }
        rotasVisitadas.retainAll(rotas);
        return new Termos(rotas, rotasVisitadas, testIds, textos);
    }

    /**
     * Só o bloco do teste que falhou: da linha com o título dele até o próximo
     * {@code it(}/{@code test(}. Sem achar o título, devolve o spec inteiro.
     * Um spec tem vários testes; cada um visita telas diferentes.
     */
    static String blocoDoTeste(String codigoSpec, String tituloTeste) {
        if (tituloTeste == null || tituloTeste.isBlank()) return codigoSpec;
        String[] partes = tituloTeste.split(" › ");
        String nome = partes[partes.length - 1].strip();
        String[] linhas = codigoSpec.split("\\R");
        int inicio = -1;
        for (int i = 0; i < linhas.length; i++) {
            if (linhas[i].contains(nome) && INICIO_DE_TESTE.matcher(linhas[i]).find()) { inicio = i; break; }
        }
        if (inicio < 0) return codigoSpec;
        StringBuilder sb = new StringBuilder(linhas[inicio]).append('\n');
        for (int i = inicio + 1; i < linhas.length && !INICIO_DE_TESTE.matcher(linhas[i]).find(); i++) {
            sb.append(linhas[i]).append('\n');
        }
        return sb.toString();
    }

    /** Rotas usadas DENTRO dos métodos chamados pelo spec (ex.: visitarLista() { goto("/admin/listarusuarios") }). */
    private static Set<String> rotasDosMetodos(String codigoPageObject, Set<String> chamadas) {
        Set<String> rotas = new LinkedHashSet<>();
        String[] linhas = codigoPageObject.split("\\R");
        String metodoAtual = null;
        for (String linha : linhas) {
            Matcher def = DEFINICAO_DE_METODO.matcher(linha);
            if (def.find()) metodoAtual = def.group(1);
            if (metodoAtual != null && chamadas.contains(metodoAtual)) coletar(ROTA, linha, rotas);
        }
        return rotas;
    }

    private static void seguirImports(Path pastaProjeto, Path arquivo, String codigo, Map<Path, String> lidos, int nivel) {
        if (nivel >= 3 || lidos.size() >= 15) return;
        Matcher m = IMPORT_RELATIVO.matcher(codigo);
        while (m.find()) {
            Path alvo = resolverArquivo(arquivo.getParent(), m.group(1));
            if (alvo == null || lidos.containsKey(alvo) || !alvo.startsWith(pastaProjeto.normalize())) continue;
            String conteudo = ler(alvo);
            if (conteudo == null) continue;
            lidos.put(alvo, conteudo);
            seguirImports(pastaProjeto, alvo, conteudo, lidos, nivel + 1);
        }
    }

    private static void coletar(Pattern p, String texto, Set<String> destino) {
        Matcher m = p.matcher(texto);
        while (m.find()) destino.add(m.group(1));
    }

    // ------------------------------------------------------------- arquivos

    private static final class Arquivo {
        final Path pasta;
        final Path caminho;
        final String[] linhas;
        final TreeSet<Integer> ocorrencias = new TreeSet<>();
        int pontos;
        boolean inteiro;

        Arquivo(Path pasta, Path caminho, String[] linhas) {
            this.pasta = pasta;
            this.caminho = caminho;
            this.linhas = linhas;
        }

        String nomeExibido() {
            return (pasta.getFileName() + "/" + pasta.relativize(caminho)).replace('\\', '/');
        }

        /** Linhas numeradas: o arquivo inteiro (tela da rota, se couber) ou janelas em volta das ocorrências. */
        String trecho() {
            String inteiroNumerado = numerar(0, linhas.length - 1);
            if ((inteiro || ocorrencias.isEmpty()) && inteiroNumerado.length() <= LIMITE_POR_ARQUIVO) return inteiroNumerado;
            StringBuilder sb = new StringBuilder();
            int fimAnterior = -1;
            for (int o : ocorrencias) {
                int ini = Math.max(o - CONTEXTO, fimAnterior + 1);
                int fim = Math.min(o + CONTEXTO, linhas.length - 1);
                if (ini > fim) continue;
                if (fimAnterior >= 0 && ini > fimAnterior + 1) sb.append("   ...\n");
                sb.append(numerar(ini, fim)).append('\n');
                fimAnterior = fim;
                if (sb.length() >= LIMITE_POR_ARQUIVO) break;
            }
            String s = sb.toString().stripTrailing();
            if (s.isEmpty()) s = inteiroNumerado;
            return s.length() <= LIMITE_POR_ARQUIVO ? s : s.substring(0, LIMITE_POR_ARQUIVO) + "\n   [... cortado ...]";
        }

        private String numerar(int ini, int fim) {
            StringBuilder sb = new StringBuilder();
            for (int i = ini; i <= fim; i++) {
                sb.append(String.format("%4d| ", i + 1)).append(linhas[i]).append('\n');
            }
            return sb.toString().stripTrailing();
        }
    }

    private static void pontuar(Arquivo a, Termos termos) {
        for (int i = 0; i < a.linhas.length; i++) {
            String linha = a.linhas[i];
            for (String r : termos.rotas) {
                if (linha.contains(r)) { a.pontos += 3; a.ocorrencias.add(i); }
            }
            for (String t : termos.testIds) {
                if (linha.contains(t)) { a.pontos += 3; a.ocorrencias.add(i); }
            }
            for (String t : termos.textos) {
                if (linha.contains(t)) { a.pontos += 2; a.ocorrencias.add(i); }
            }
        }
    }

    /** Acha o arquivo do componente pelo import dele no mesmo arquivo: {@code import X from './views/x'}. */
    private static Path resolverImport(Arquivo a, String componente) {
        Pattern imp = Pattern.compile("import\\s+" + Pattern.quote(componente) + "\\s+from\\s+[\"']([^\"']+)[\"']");
        for (String linha : a.linhas) {
            Matcher m = imp.matcher(linha);
            if (m.find() && m.group(1).startsWith(".")) return resolverArquivo(a.caminho.getParent(), m.group(1));
        }
        return null;
    }

    /** "./views/admin/showUsers" → arquivo existente, testando as extensões e index.*. */
    private static Path resolverArquivo(Path base, String relativo) {
        Path p = base.resolve(relativo).normalize();
        List<Path> candidatos = new ArrayList<>(List.of(p));
        for (String ext : new String[] {".js", ".jsx", ".ts", ".tsx"}) {
            candidatos.add(Path.of(p + ext));
            candidatos.add(p.resolve("index" + ext));
        }
        return candidatos.stream().filter(Files::isRegularFile).findFirst().map(Path::normalize).orElse(null);
    }

    private static List<Path> listarCodigo(Path pasta) {
        try (Stream<Path> s = Files.walk(pasta)) {
            return s.filter(Files::isRegularFile)
                    .filter(f -> pasta.relativize(f).toString().replace('\\', '/').chars().filter(c -> c == '/').count() < 10)
                    .filter(f -> {
                        for (Path parte : pasta.relativize(f)) if (PASTAS_IGNORADAS.contains(parte.toString())) return false;
                        return true;
                    })
                    .filter(f -> EXTENSOES.matcher(f.getFileName().toString()).matches())
                    .filter(f -> !ARQUIVO_DE_TESTE.matcher(f.getFileName().toString()).matches())
                    .filter(f -> tamanho(f) <= TAMANHO_MAXIMO_ARQUIVO)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long tamanho(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static String ler(Path f) {
        try {
            return Files.readString(f);
        } catch (IOException | RuntimeException e) {
            return null; // binário, encoding estranho ou sumiu: ignora o arquivo
        }
    }
}
