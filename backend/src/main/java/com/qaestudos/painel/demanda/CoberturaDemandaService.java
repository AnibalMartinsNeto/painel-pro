package com.qaestudos.painel.demanda;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Modulo;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ResumoSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * MAPA DE COBERTURA da demanda: o que ela pede × o que já tem teste
 * automatizado (com evidência) × o que falta (com cenário sugerido).
 *
 * <p>Só LEITURA e sugestão: nada é criado no Jira nem no código. Uma
 * chamada à IA, com o mínimo de contexto que basta:
 * <ul>
 *   <li>a demanda (título e descrição, lidos do Jira);
 *   <li>um RESUMO de cada spec candidato — estrutura, asserções e Page
 *       Objects ({@link ResumoSpec#estrutura}), não o arquivo inteiro;
 *   <li>as regras de negócio do projeto, como referência do esperado.
 * </ul>
 * Candidatos: specs que citam a chave em TODOS os projetos de teste (a mesma
 * demanda costuma ser coberta por E2E, API e desempenho); sem nenhum, os dos
 * módulos citados na demanda no projeto escolhido; sem módulo, todos dele.
 */
@Service
public class CoberturaDemandaService {

    private static final Pattern CHAVE_ISSUE = Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");
    private static final int MAX_SPECS = 12;
    private static final int LIMITE_DESCRICAO = 6_000;
    private static final int LIMITE_REGRAS = 8_000;

    public enum Situacao { COBERTO, PARCIAL, SEM_TESTE }

    /**
     * @param evidencias      testes que cobrem o requisito ("spec › teste")
     * @param cenarioSugerido o que testar quando não está (todo) coberto
     */
    public record Requisito(String requisito, Situacao situacao, List<String> evidencias, String cenarioSugerido) {}

    /** @param criterioSpecs como os specs foram escolhidos (para o QA entender a base da análise) */
    public record Cobertura(String chave, String titulo, String url, String resumo, List<Requisito> requisitos,
                            List<String> specsAnalisados, String criterioSpecs, String modelo) {}

    private final JiraCliente jira;
    private final ProjetoService projetos;
    private final AssistenteTriagem ia;

    public CoberturaDemandaService(JiraCliente jira, ProjetoService projetos, AssistenteTriagem ia) {
        this.jira = jira;
        this.projetos = projetos;
        this.ia = ia;
    }

    public Cobertura mapear(String projetoId, String digitada) {
        String chave = digitada == null ? "" : digitada.trim().toUpperCase(Locale.ROOT);
        if (!CHAVE_ISSUE.matcher(chave).matches()) {
            throw new RequisicaoInvalidaException("'%s' não é uma chave de issue do Jira (formato: PROJETO-123).".formatted(digitada));
        }
        Projeto projeto = projetos.buscar(projetoId);
        JiraCliente.DetalheIssue demanda = jira.buscarDetalhe(chave);

        // 1. Specs candidatos, do mais específico para o mais amplo.
        List<Alvo> alvos = new ArrayList<>();
        for (Projeto p : projetos.listar()) {
            projetos.specsQueCitam(p, chave).forEach(spec -> alvos.add(new Alvo(p, spec)));
        }
        String criterio = "specs que citam " + chave + " (todos os projetos de teste)";
        if (alvos.isEmpty()) {
            String texto = normalizar(demanda.resumo() + " " + demanda.descricao());
            List<Modulo> citados = projeto.modulos().stream()
                    .filter(m -> texto.contains(normalizar(m.termo())) || texto.contains(normalizar(m.rotulo())))
                    .toList();
            projetos.listarSpecs(projeto).stream().filter(sp -> citados.stream().anyMatch(m -> m.cobre(sp)))
                    .forEach(sp -> alvos.add(new Alvo(projeto, sp)));
            criterio = "specs de " + projeto.nome() + " dos módulos citados na demanda ("
                    + String.join(", ", citados.stream().map(Modulo::rotulo).toList()) + ")";
        }
        if (alvos.isEmpty()) {
            projetos.listarSpecs(projeto).forEach(sp -> alvos.add(new Alvo(projeto, sp)));
            criterio = "todos os specs de " + projeto.nome() + " (nenhum cita a demanda nem um módulo dela)";
        }
        List<Alvo> analisados = alvos.stream().limit(MAX_SPECS).toList();

        // 2. Uma chamada à IA.
        var resposta = ia.gerarTexto(prompt(demanda, analisados));
        JsonNode j = ia.lerJson(resposta.texto());

        List<Requisito> requisitos = new ArrayList<>();
        for (JsonNode r : j.path("requisitos")) {
            List<String> evidencias = new ArrayList<>();
            r.path("evidencias").forEach(e -> evidencias.add(e.asString("")));
            requisitos.add(new Requisito(r.path("requisito").asString(""), situacao(r.path("situacao").asString(null)),
                    evidencias, r.path("cenarioSugerido").asString(null)));
        }
        return new Cobertura(demanda.chave(), demanda.resumo(), demanda.url(), j.path("resumo").asString(null), requisitos,
                analisados.stream().map(Alvo::rotulo).toList(), criterio, resposta.modelo());
    }

    /** Um spec de um projeto de teste ("playwright: tests/login.spec.js"). */
    record Alvo(Projeto projeto, String spec) {
        String rotulo() {
            return projeto.id() + ": " + spec;
        }
    }

    String prompt(JiraCliente.DetalheIssue demanda, List<Alvo> alvos) {
        StringBuilder testes = new StringBuilder();
        for (Alvo alvo : alvos) {
            String codigo = ler(alvo.projeto(), alvo.spec());
            testes.append("Spec (").append(alvo.projeto().nome()).append("): ").append(alvo.spec()).append('\n')
                    .append(codigo == null ? "(arquivo não encontrado)" : ResumoSpec.estrutura(codigo)).append("\n\n");
        }
        // Os projetos de teste do mesmo sistema compartilham o arquivo de regras: o do primeiro serve.
        String regras = alvos.stream().map(a -> projetos.regras(a.projeto())).flatMap(java.util.Optional::stream).findFirst()
                .map(r -> cortar(r, LIMITE_REGRAS)).orElse("(o projeto não tem arquivo de regras)");
        return """
                Você é um analista de QA sênior. Monte o MAPA DE COBERTURA de uma demanda: o que ela pede, o que
                já tem teste automatizado (com evidência) e o que falta testar. Responda em português do Brasil.

                Demanda %s (%s) — %s
                Descrição:
                \"\"\"
                %s
                \"\"\"

                Regras de negócio do sistema (referência do comportamento esperado):
                \"\"\"
                %s
                \"\"\"

                Testes automatizados existentes (resumo de cada spec: estrutura, asserções, Page Objects, API):
                \"\"\"
                %s
                \"\"\"

                Quebre a demanda em requisitos verificáveis (use também as regras citadas por ela). Para cada um:
                - COBERTO: algum teste verifica de fato (cite em "evidencias" como "spec › título do teste", só specs listados acima);
                - PARCIAL: há teste, mas falta um caminho importante (diga qual no cenário sugerido);
                - SEM_TESTE: nenhum teste verifica (sugira o cenário).
                Não invente testes que não estão no resumo.

                Responda SOMENTE com um JSON válido, sem markdown, no formato:
                {
                  "resumo": "1 a 2 frases: quanto da demanda está coberto e o principal buraco",
                  "requisitos": [
                    {"requisito": "...", "situacao": "COBERTO" | "PARCIAL" | "SEM_TESTE",
                     "evidencias": ["spec › teste"], "cenarioSugerido": "... ou null se COBERTO"}
                  ]
                }
                """.formatted(demanda.chave(), demanda.tipo(), demanda.resumo(),
                cortar(demanda.descricao().isBlank() ? "(sem descrição)" : demanda.descricao(), LIMITE_DESCRICAO),
                regras, testes.toString().strip());
    }

    private static Situacao situacao(String s) {
        try {
            return s == null ? Situacao.SEM_TESTE : Situacao.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Situacao.SEM_TESTE;
        }
    }

    private static String ler(Projeto projeto, String spec) {
        try {
            return Files.readString(projeto.diretorio().resolve(spec));
        } catch (IOException e) {
            return null;
        }
    }

    private static String cortar(String s, int limite) {
        return s.length() <= limite ? s : s.substring(0, limite) + "\n[... cortado ...]";
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
