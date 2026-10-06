package com.qaestudos.painel.jira;

import com.qaestudos.painel.common.ConflitoException;
import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.ResultadoTesteRepository;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.triagem.Severidade;
import com.qaestudos.painel.triagem.Triagem;
import com.qaestudos.painel.triagem.TriagemRepository;
import java.time.Clock;
import com.qaestudos.painel.triagem.JiraVinculoRepository;
import com.qaestudos.painel.triagem.JiraVinculo;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Leva a triagem ao Jira: cria o bug e o liga à demanda testada; e busca as
 * demandas e os specs que as citam.
 *
 * <p>Mesmo cuidado da triagem com IA: as chamadas ao Jira (serviço externo)
 * acontecem FORA das transações do banco. Fluxo de {@link #publicar}:
 * lê (transação curta) → cria o bug e o vínculo no Jira → grava a chave.
 */
@Service
public class PublicacaoJiraService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoJiraService.class);
    private static final Pattern CHAVE_ISSUE = Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final TriagemRepository triagens;
    private final JiraVinculoRepository vinculos;
    private final ResultadoTesteRepository resultados;
    private final ProjetoService projetos;
    private final JiraCliente jira;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PublicacaoJiraService(TriagemRepository triagens, JiraVinculoRepository vinculos, ResultadoTesteRepository resultados,
                                 ProjetoService projetos,
                                 JiraCliente jira, TransactionTemplate tx, Clock clock) {
        this.triagens = triagens;
        this.vinculos = vinculos;
        this.resultados = resultados;
        this.projetos = projetos;
        this.jira = jira;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param origem preenchido quando a chave é um bug que o painel publicou:
     *               o teste que o encontrou, para retestar a correção
     */
    public record Demanda(String chave, JiraCliente.Issue issue, String erro, List<String> specs, OrigemBug origem) {}

    /**
     * De onde veio um bug publicado pelo painel.
     *
     * @param specExiste false se o arquivo do teste foi apagado/renomeado depois
     */
    public record OrigemBug(String spec, String teste, String demanda, Instant publicadaEm, boolean specExiste) {}

    public record Publicacao(String chave, String url, String demanda, String aviso) {}

    /** Normaliza e valida a chave digitada ("dev-1 " → "DEV-1"). */
    static String chave(String digitada) {
        String c = digitada == null ? "" : digitada.trim().toUpperCase(Locale.ROOT);
        if (!CHAVE_ISSUE.matcher(c).matches()) {
            throw new RequisicaoInvalidaException("'%s' não é uma chave de issue do Jira (formato: PROJETO-123).".formatted(digitada));
        }
        return c;
    }

    /** A demanda no Jira e os specs do projeto que a citam. Se o Jira falhar, ainda devolve os specs. */
    public Demanda buscarDemanda(String projetoId, String digitada) {
        String c = chave(digitada);
        Projeto projeto = projetos.buscar(projetoId);
        List<String> specs = projetos.specsQueCitam(projeto, c);
        OrigemBug origem = triagens.findByProjetoIdAndJiraIssue(projetoId, c)
                .map(t -> origemDe(t, projetos.listarSpecs(projeto)))
                .orElse(null);
        try {
            return new Demanda(c, jira.buscarIssue(c), null, specs, origem);
        } catch (RequisicaoInvalidaException e) {
            return new Demanda(c, null, e.getMessage(), specs, origem);
        }
    }

    /** chaveTeste = "spec › describe › teste": o spec é o primeiro pedaço. */
    static OrigemBug origemDe(Triagem t, List<String> specsDoProjeto) {
        String[] partes = t.getChaveTeste().split(" › ", 2);
        String spec = partes[0];
        String teste = partes.length > 1 ? partes[1] : t.getChaveTeste();
        return new OrigemBug(spec, teste, t.getDemanda(), t.getPublicadaEm(), specsDoProjeto.contains(spec));
    }

    public List<Triagem> listarPublicados(String projetoId) {
        projetos.buscar(projetoId);
        return triagens.findByProjetoIdAndJiraIssueIsNotNullOrderByPublicadaEmDesc(projetoId);
    }

    /** Histórico do projeto no Jira: as issues mais recentes, criadas pelo painel ou não. */
    public List<JiraCliente.IssueHistorico> historico(int maximo) {
        return jira.ultimasIssues(Math.clamp(maximo, 1, 100));
    }

    /** Dados lidos do banco antes de falar com o Jira. */
    private record Alvo(Long triagemId, JiraCliente.NovoBug bug) {}

    public Publicacao publicar(Long resultadoId, String demandaDigitada) {
        return publicar(resultadoId, demandaDigitada, false);
    }

    /**
     * Cria o bug no Jira e o liga à demanda.
     *
     * @param novoBug true = cria um bug NOVO mesmo o teste já tendo um (ex.: o
     *                antigo foi fechado e a falha voltou). Sem isso, 409.
     */
    public Publicacao publicar(Long resultadoId, String demandaDigitada, boolean novoBug) {
        String demanda = demandaDigitada == null || demandaDigitada.isBlank() ? null : chave(demandaDigitada);
        Alvo alvo = tx.execute(s -> preparar(resultadoId, novoBug));            // 1. lê, valida e RESERVA
        try {
            JiraCliente.Issue bug = jira.criarBug(alvo.bug());                     // 2. Jira, sem banco
            String aviso = null;
            if (demanda != null) {
                try {
                    jira.vincular(bug.chave(), demanda);
                } catch (RuntimeException e) {
                    // O bug JÁ existe no Jira: não desfaz; registra e avisa o QA.
                    log.warn("Bug {} criado, mas não foi possível ligá-lo a {}", bug.chave(), demanda, e);
                    aviso = "Bug criado, mas não foi possível ligá-lo a %s. Faça a ligação manualmente no Jira.".formatted(demanda);
                }
            }
            tx.executeWithoutResult(s -> {                                         // 3. grava + histórico
                Triagem t = triagens.findById(alvo.triagemId()).orElseThrow();
                t.registrarPublicacao(bug.chave(), bug.url(), demanda, clock.instant());
                vinculos.save(new JiraVinculo(t.getProjetoId(), t.getChaveTeste(), resultadoId, bug.chave(), bug.url(),
                        JiraVinculo.Acao.CRIADO, clock.instant()));
            });
            return new Publicacao(bug.chave(), bug.url(), demanda, aviso);
        } finally {
            tx.executeWithoutResult(s -> triagens.liberarPublicacao(alvo.triagemId()));
        }
    }

    public record Comentario(String chave, String url) {}

    /**
     * Falha RECORRENTE: em vez de criar outro bug, comenta a nova ocorrência no
     * bug que já existe (execução, data, navegador e o erro de agora).
     */
    public Comentario comentarOcorrencia(Long resultadoId) {
        AlvoComentario a = tx.execute(s -> prepararComentario(resultadoId));
        jira.comentar(a.issue(), a.corpo());
        tx.executeWithoutResult(s -> {
            Triagem t = triagens.findById(a.triagemId()).orElseThrow();
            t.registrarComentario(resultadoId);
            vinculos.save(new JiraVinculo(t.getProjetoId(), t.getChaveTeste(), resultadoId, a.issue(), a.url(),
                    JiraVinculo.Acao.COMENTADO, clock.instant()));
        });
        return new Comentario(a.issue(), a.url());
    }

    private record AlvoComentario(Long triagemId, String issue, String url, Map<String, Object> corpo) {}

    private AlvoComentario prepararComentario(Long resultadoId) {
        ResultadoTeste r = resultados.buscarComExecucao(resultadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Resultado de teste %d não existe.".formatted(resultadoId)));
        Triagem t = triagens.findByProjetoIdAndChaveTeste(r.getExecucao().getProjetoId(), r.getChave())
                .filter(Triagem::publicada)
                .orElseThrow(() -> new RequisicaoInvalidaException("Este teste ainda não tem bug no Jira para comentar."));
        if (resultadoId.equals(t.getComentadoResultadoId())) {
            throw new ConflitoException("Esta ocorrência já foi comentada em %s.".formatted(t.getJiraIssue()));
        }
        var corpo = new DocumentoAdf()
                .paragrafo("Nova ocorrência desta falha, detectada pelo QA Panel Pro.")
                .titulo("Teste")
                .paragrafo(r.getSpec() + " › " + r.getTitulo())
                .titulo("Erro desta execução")
                .codigo(r.getMensagemErro())
                .paragrafo("Execução #%d em %s · navegador: %s".formatted(r.getExecucao().getId(),
                        DATA.format(r.getExecucao().getIniciadaEm()), valor(r.getExecucao().getNavegador())))
                .montar();
        return new AlvoComentario(t.getId(), t.getJiraIssue(), t.getJiraUrl(), corpo);
    }

    private Alvo preparar(Long resultadoId, boolean novoBug) {
        ResultadoTeste r = resultados.buscarComExecucao(resultadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Resultado de teste %d não existe.".formatted(resultadoId)));
        String projetoId = r.getExecucao().getProjetoId();
        Triagem t = triagens.findByProjetoIdAndChaveTeste(projetoId, r.getChave())
                .orElseThrow(() -> new RequisicaoInvalidaException("Salve a triagem antes de publicar no Jira."));
        if (t.publicada() && !novoBug) {
            throw new ConflitoException("Este teste já tem bug publicado: %s (%s).".formatted(t.getJiraIssue(), t.getJiraUrl()));
        }
        if (t.getTitulo() == null || t.getTitulo().isBlank()) {
            throw new RequisicaoInvalidaException("A triagem precisa de um título para virar bug no Jira.");
        }
        // Reserva ATÔMICA: um segundo pedido simultâneo (clique duplo, duas abas) para aqui.
        Instant agora = clock.instant();
        if (triagens.reservarPublicacao(t.getId(), agora, agora.minus(Duration.ofMinutes(2))) == 0) {
            throw new ConflitoException("A publicação deste bug já está em andamento. Aguarde alguns segundos.");
        }
        Projeto projeto = projetos.buscar(projetoId);
        var descricao = new DocumentoAdf()
                .paragrafo("Bug encontrado por teste automatizado (%s) e triado no QA Panel Pro.".formatted(projeto.nome()))
                .titulo("Teste")
                .paragrafo(r.getSpec() + " › " + r.getTitulo())
                .titulo("Passos para reproduzir")
                .listaNumerada(t.getPassos())
                .titulo("Resultado esperado")
                .paragrafo(valor(t.getEsperado()))
                .titulo("Resultado encontrado")
                .paragrafo(valor(t.getEncontrado()))
                .titulo("Análise")
                .paragrafo(valor(t.getAnalise()))
                .titulo("Erro do teste")
                .codigo(r.getMensagemErro())
                .paragrafo("Execução #%d em %s · navegador: %s".formatted(r.getExecucao().getId(),
                        DATA.format(r.getExecucao().getIniciadaEm()), valor(r.getExecucao().getNavegador())))
                .montar();
        return new Alvo(t.getId(), new JiraCliente.NovoBug(t.getTitulo(), descricao, prioridade(t.getSeveridade()),
                List.of("qa-panel", projetoId)));
    }

    /** Severidade da triagem → prioridade do Jira. */
    static String prioridade(Severidade s) {
        if (s == null) return null;
        return switch (s) {
            case CRITICA -> "Highest";
            case ALTA -> "High";
            case MEDIA -> "Medium";
            case BAIXA -> "Low";
        };
    }

    private static String valor(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
