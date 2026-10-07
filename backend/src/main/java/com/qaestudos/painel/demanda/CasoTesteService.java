package com.qaestudos.painel.demanda;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.jira.DocumentoAdf;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ResumoSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * CASOS DE TESTE da demanda, escritos pela IA e revisados pelo QA.
 *
 * <p>A IA lê a demanda (título e descrição), as regras de negócio e o que já
 * existe de teste automatizado, e propõe casos positivos, negativos e de
 * limite — cada um ligado à regra e marcado se já tem automação. O QA edita
 * e publica como comentário na demanda (funciona em qualquer Jira, sem
 * depender de um tipo de issue "Caso de teste").
 */
@Service
public class CasoTesteService {

    private static final Pattern CHAVE_ISSUE = Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");
    private static final int LIMITE_REGRAS = 8_000;
    private static final int LIMITE_DESCRICAO = 6_000;

    public enum TipoCaso { POSITIVO, NEGATIVO, LIMITE }

    /**
     * @param regra       código da regra de negócio coberta (ex.: USU-03), ou null
     * @param automatizado já existe teste automatizado que verifica este caso
     * @param evidencia   o teste que o automatiza ("spec › teste"), quando houver
     */
    public record Caso(String titulo, TipoCaso tipo, String preCondicoes, List<String> passos, String resultadoEsperado,
                       String regra, boolean automatizado, String evidencia) {}

    public record Rascunho(String chave, String titulo, String url, List<Caso> casos, String modelo) {}

    public record Publicado(String chave, String url, int casos) {}

    private final JiraCliente jira;
    private final ProjetoService projetos;
    private final AssistenteTriagem ia;

    public CasoTesteService(JiraCliente jira, ProjetoService projetos, AssistenteTriagem ia) {
        this.jira = jira;
        this.projetos = projetos;
        this.ia = ia;
    }

    public Rascunho rascunho(String projetoId, String digitada) {
        String chave = chave(digitada);
        Projeto projeto = projetos.buscar(projetoId);
        JiraCliente.DetalheIssue demanda = jira.buscarDetalhe(chave);

        StringBuilder existentes = new StringBuilder();
        for (Projeto p : projetos.listar()) {
            for (String spec : projetos.specsQueCitam(p, chave)) {
                existentes.append("Spec (").append(p.nome()).append("): ").append(spec).append('\n')
                        .append(ResumoSpec.estrutura(ler(p, spec))).append("\n\n");
            }
        }
        String regras = projetos.regras(projeto).map(r -> r.length() <= LIMITE_REGRAS ? r : r.substring(0, LIMITE_REGRAS))
                .orElse("(o projeto não tem arquivo de regras)");
        String descricao = demanda.descricao().isBlank() ? "(sem descrição)" : demanda.descricao();

        var resposta = ia.gerarTexto("""
                Você é um analista de QA sênior. Escreva os CASOS DE TESTE da demanda abaixo, em português do Brasil.

                Demanda %s (%s) — %s
                Descrição:
                \"\"\"
                %s
                \"\"\"

                Regras de negócio do sistema:
                \"\"\"
                %s
                \"\"\"

                Testes automatizados que já existem para esta demanda (resumo):
                \"\"\"
                %s
                \"\"\"

                Cubra o caminho feliz (POSITIVO), entradas inválidas e erros (NEGATIVO) e fronteiras (LIMITE).
                Passos objetivos, reproduzíveis por outra pessoa. Ligue cada caso à regra que ele verifica.
                Marque "automatizado": true só se um teste listado acima verifica o caso (cite em "evidencia").

                Responda SOMENTE com um JSON válido, sem markdown:
                {"casos": [{"titulo": "...", "tipo": "POSITIVO" | "NEGATIVO" | "LIMITE", "preCondicoes": "...",
                  "passos": ["..."], "resultadoEsperado": "...", "regra": "USU-03 ou null",
                  "automatizado": true | false, "evidencia": "spec › teste ou null"}]}
                """.formatted(demanda.chave(), demanda.tipo(), demanda.resumo(),
                descricao.length() <= LIMITE_DESCRICAO ? descricao : descricao.substring(0, LIMITE_DESCRICAO),
                regras, existentes.isEmpty() ? "(nenhum)" : existentes.toString().strip()));

        JsonNode j = ia.lerJson(resposta.texto());
        List<Caso> casos = new ArrayList<>();
        for (JsonNode c : j.path("casos")) {
            List<String> passos = new ArrayList<>();
            c.path("passos").forEach(p -> passos.add(p.asString("")));
            casos.add(new Caso(c.path("titulo").asString(""), tipo(c.path("tipo").asString(null)),
                    texto(c.path("preCondicoes")), passos, texto(c.path("resultadoEsperado")), texto(c.path("regra")),
                    c.path("automatizado").asBoolean(false), texto(c.path("evidencia"))));
        }
        return new Rascunho(demanda.chave(), demanda.resumo(), demanda.url(), casos, resposta.modelo());
    }

    /** Publica os casos revisados como comentário na demanda. */
    public Publicado publicar(String digitada, List<Caso> casos) {
        String chave = chave(digitada);
        List<Caso> validos = casos == null ? List.of() : casos.stream().filter(c -> c.titulo() != null && !c.titulo().isBlank()).toList();
        if (validos.isEmpty()) throw new RequisicaoInvalidaException("Não há casos de teste para publicar.");

        long automatizados = validos.stream().filter(Caso::automatizado).count();
        var doc = new DocumentoAdf()
                .titulo("Casos de teste do QA (%d)".formatted(validos.size()))
                .paragrafo("%d de %d já têm teste automatizado.".formatted(automatizados, validos.size()));
        int n = 1;
        for (Caso c : validos) {
            doc.titulo("CT%02d — %s [%s]%s".formatted(n++, c.titulo(), c.tipo(), c.regra() == null ? "" : " · " + c.regra()))
                    .paragrafo(c.preCondicoes() == null ? null : "Pré-condições: " + c.preCondicoes())
                    .listaNumerada(c.passos())
                    .paragrafo("Resultado esperado: " + (c.resultadoEsperado() == null ? "—" : c.resultadoEsperado()))
                    .paragrafo(c.automatizado() ? "Automatizado: " + (c.evidencia() == null ? "sim" : c.evidencia()) : "Ainda sem teste automatizado.");
        }
        doc.paragrafo("Gerado pelo QA Panel Pro e revisado pelo QA.");
        jira.comentar(chave, doc.montar());
        return new Publicado(chave, jira.buscarIssue(chave).url(), validos.size());
    }

    private static TipoCaso tipo(String s) {
        try {
            return s == null ? TipoCaso.POSITIVO : TipoCaso.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TipoCaso.POSITIVO;
        }
    }

    /** Texto do JSON, tratando "null" escrito pela IA como ausente. */
    private static String texto(JsonNode n) {
        String s = n.asString(null);
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s.strip()) ? null : s;
    }

    private static String chave(String digitada) {
        String c = digitada == null ? "" : digitada.trim().toUpperCase(Locale.ROOT);
        if (!CHAVE_ISSUE.matcher(c).matches()) {
            throw new RequisicaoInvalidaException("'%s' não é uma chave de issue do Jira (formato: PROJETO-123).".formatted(digitada));
        }
        return c;
    }

    private static String ler(Projeto projeto, String spec) {
        try {
            return Files.readString(projeto.diretorio().resolve(spec));
        } catch (IOException e) {
            return null;
        }
    }
}
