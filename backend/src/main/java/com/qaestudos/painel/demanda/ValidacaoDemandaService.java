package com.qaestudos.painel.demanda;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.configuracao.ChaveConfig;
import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.execucao.ResultadoTesteRepository;
import com.qaestudos.painel.execucao.ResultadoTesteRepository.UltimoResultado;
import com.qaestudos.painel.jira.DocumentoAdf;
import com.qaestudos.painel.jira.JiraCliente;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.triagem.AssistenteTriagem;
import com.qaestudos.painel.triagem.ResumoSpec;
import com.qaestudos.painel.triagem.ia.IaIndisponivelException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * RELATÓRIO DE VALIDAÇÃO da demanda: o fechamento do trabalho do QA.
 *
 * <ol>
 *   <li>junta o resultado MAIS RECENTE (execução real) de cada teste que cita
 *       a demanda, em todos os projetos de teste;
 *   <li>o VEREDITO vem dos fatos, nunca da IA: tudo passou → APROVADA,
 *       algo falhou → REPROVADA;
 *   <li>a IA (se configurada) só escreve, em linguagem de QA, o que cada teste
 *       validou e as pendências; sem IA, um rascunho direto dos resultados;
 *   <li>o QA revisa e publica como comentário na demanda.
 * </ol>
 */
@Service
public class ValidacaoDemandaService {

    private static final Pattern CHAVE_ISSUE = Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public enum Veredito { APROVADA, REPROVADA }

    /** Um teste da demanda e o último resultado dele. */
    public record Item(String projeto, String spec, String teste, String status, Long execucaoId, Instant quando,
                       String oQueFoiValidado) {}

    public record Rascunho(String chave, String titulo, String url, Veredito veredito, String resumo, List<Item> itens,
                           List<String> pendencias, String origem, String modelo) {}

    /** O que o QA publica (revisado na tela). */
    public record Publicar(Veredito veredito, String resumo, List<Item> itens, List<String> pendencias) {}

    public record Publicado(String chave, String url) {}

    private final JiraCliente jira;
    private final ProjetoService projetos;
    private final ResultadoTesteRepository resultados;
    private final AssistenteTriagem ia;
    private final ConfiguracaoService config;

    public ValidacaoDemandaService(JiraCliente jira, ProjetoService projetos, ResultadoTesteRepository resultados,
                                   AssistenteTriagem ia, ConfiguracaoService config) {
        this.jira = jira;
        this.projetos = projetos;
        this.resultados = resultados;
        this.ia = ia;
        this.config = config;
    }

    public Rascunho rascunho(String digitada) {
        String chave = chave(digitada);
        JiraCliente.DetalheIssue demanda = jira.buscarDetalhe(chave);

        // 1. Último resultado real de cada teste que cita a demanda, em todos os projetos.
        List<Item> itens = new ArrayList<>();
        Map<String, String> resumos = new LinkedHashMap<>();
        for (Projeto p : projetos.listar()) {
            List<String> specs = projetos.specsQueCitam(p, chave);
            if (specs.isEmpty()) continue;
            for (UltimoResultado r : resultados.ultimosResultados(p.id(), specs)) {
                itens.add(new Item(p.nome(), r.getSpec(), r.getTitulo(), r.getStatus(), r.getExecucaoId(), r.getQuando(), null));
            }
            specs.forEach(s -> resumos.put(p.nome() + ": " + s, ResumoSpec.estrutura(ler(p, s))));
        }
        if (itens.isEmpty()) {
            throw new RequisicaoInvalidaException(
                    "Nenhum teste que cita %s foi executado (em execução real) ainda. Rode os specs da demanda primeiro.".formatted(chave));
        }
        // 2. Veredito pelos FATOS.
        Veredito veredito = itens.stream().allMatch(i -> "PASSOU".equals(i.status())) ? Veredito.APROVADA : Veredito.REPROVADA;

        // 3. Texto: IA se houver chave; senão, direto dos resultados.
        if (iaConfigurada()) {
            try {
                return comIa(demanda, veredito, itens, resumos);
            } catch (IaIndisponivelException e) {
                // Sem a IA (cota, rede), o relatório continua possível: cai no rascunho direto.
            }
        }
        long falhas = itens.stream().filter(i -> !"PASSOU".equals(i.status())).count();
        String resumo = veredito == Veredito.APROVADA
                ? "Todos os %d testes automatizados da demanda passaram na execução mais recente.".formatted(itens.size())
                : "%d de %d testes automatizados da demanda falharam na execução mais recente.".formatted(falhas, itens.size());
        return new Rascunho(demanda.chave(), demanda.resumo(), demanda.url(), veredito, resumo, itens, List.of(), "HEURISTICA", null);
    }

    private Rascunho comIa(JiraCliente.DetalheIssue demanda, Veredito veredito, List<Item> itens, Map<String, String> resumos) {
        StringBuilder testes = new StringBuilder();
        for (int i = 0; i < itens.size(); i++) {
            Item it = itens.get(i);
            testes.append(i).append(". [").append(it.status()).append("] ").append(it.projeto()).append(" › ")
                    .append(it.spec()).append(" › ").append(it.teste()).append('\n');
        }
        StringBuilder codigo = new StringBuilder();
        resumos.forEach((spec, resumo) -> codigo.append("Spec ").append(spec).append(":\n").append(resumo).append("\n\n"));
        var resposta = ia.gerarTexto("""
                Você é um analista de QA sênior escrevendo o RELATÓRIO DE VALIDAÇÃO de uma demanda, em português do Brasil.
                O veredito já foi decidido pelos resultados: %s. Não o altere.

                Demanda %s — %s
                Descrição:
                \"\"\"
                %s
                \"\"\"

                Testes da demanda com o último resultado (índice. [status] projeto › spec › teste):
                %s
                Resumo do código de cada spec (o que ele verifica):
                \"\"\"
                %s
                \"\"\"

                Para cada teste, descreva em 1 frase O QUE FOI VALIDADO de fato (com base nas asserções, não só no título).
                Liste pendências: requisitos da demanda sem teste e testes que falharam.
                Responda SOMENTE com um JSON válido, sem markdown:
                {"resumo": "2 a 3 frases para o time", "validacoes": [{"indice": 0, "oQueFoiValidado": "..."}],
                 "pendencias": ["..."]}
                """.formatted(veredito, demanda.chave(), demanda.resumo(), demanda.descricao().isBlank() ? "(sem descrição)" : demanda.descricao(),
                testes, codigo.toString().strip()));
        JsonNode j = ia.lerJson(resposta.texto());
        List<Item> descritos = new ArrayList<>(itens);
        for (JsonNode v : j.path("validacoes")) {
            int i = v.path("indice").asInt(-1);
            if (i >= 0 && i < descritos.size()) {
                Item it = descritos.get(i);
                descritos.set(i, new Item(it.projeto(), it.spec(), it.teste(), it.status(), it.execucaoId(), it.quando(),
                        v.path("oQueFoiValidado").asString(null)));
            }
        }
        List<String> pendencias = new ArrayList<>();
        j.path("pendencias").forEach(p -> pendencias.add(p.asString("")));
        return new Rascunho(demanda.chave(), demanda.resumo(), demanda.url(), veredito, j.path("resumo").asString(null), descritos,
                pendencias, "IA", resposta.modelo());
    }

    /** Publica o relatório revisado como comentário na demanda. */
    public Publicado publicar(String digitada, Publicar r) {
        String chave = chave(digitada);
        if (r == null || r.veredito() == null || r.itens() == null || r.itens().isEmpty()) {
            throw new RequisicaoInvalidaException("Gere o relatório de validação antes de publicar.");
        }
        List<String> linhas = r.itens().stream().map(i -> "%s %s › %s — %s (execução #%d, %s)%s".formatted(
                "PASSOU".equals(i.status()) ? "✅" : "❌", i.projeto(), i.spec(), i.teste(), i.execucaoId(),
                i.quando() == null ? "-" : DATA.format(i.quando()),
                i.oQueFoiValidado() == null || i.oQueFoiValidado().isBlank() ? "" : ": " + i.oQueFoiValidado())).toList();
        var doc = new DocumentoAdf()
                .titulo("Relatório de validação do QA — " + (r.veredito() == Veredito.APROVADA ? "APROVADA ✅" : "REPROVADA ❌"))
                .paragrafo(r.resumo())
                .titulo("Testes automatizados")
                .listaNumerada(linhas);
        if (r.pendencias() != null && !r.pendencias().isEmpty()) doc.titulo("Pendências").listaNumerada(r.pendencias());
        doc.paragrafo("Gerado pelo QA Panel Pro a partir das execuções reais mais recentes.");
        jira.comentar(chave, doc.montar());
        return new Publicado(chave, jira.buscarIssue(chave).url());
    }

    private boolean iaConfigurada() {
        boolean gemini = "gemini".equals(config.valor(ChaveConfig.IA_PROVEDOR).orElse("gemini"));
        return config.valor(gemini ? ChaveConfig.IA_GEMINI_CHAVE : ChaveConfig.IA_ANTHROPIC_CHAVE).filter(c -> !c.isBlank()).isPresent();
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
