package com.qaestudos.painel.triagem;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** ENTIDADE JPA da tabela {@code triagem}: a análise de um teste que falha. */
@Entity
@Table(name = "triagem")
public class Triagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String projetoId;
    private String chaveTeste;
    private Long resultadoId;

    @Enumerated(EnumType.STRING)
    private Classificacao classificacao;

    @Enumerated(EnumType.STRING)
    private Classificacao classificacaoSugerida;

    @Enumerated(EnumType.STRING)
    private Severidade severidade;

    private String titulo;
    private String esperado;
    private String encontrado;
    private String passos; // no banco: um passo por linha
    private String analise;
    private String origemRascunho;
    private String modelo;
    private String observacoes;
    private Instant atualizadaEm;
    private String jiraIssue;
    private String jiraUrl;
    private String demanda;
    private Instant publicadaEm;
    private Long ignoradoResultadoId;
    private Instant ignoradoEm;
    private Long comentadoResultadoId;
    private Instant publicandoEm;

    protected Triagem() {}

    /**
     * Registra o bug criado no Jira. Normalmente uma triagem tem um bug só; um
     * "novo bug" (o antigo foi fechado) substitui o atual — o histórico completo
     * fica em {@link JiraVinculo}.
     */
    public void registrarPublicacao(String jiraIssue, String jiraUrl, String demanda, Instant agora) {
        this.jiraIssue = jiraIssue;
        this.jiraUrl = jiraUrl;
        this.demanda = demanda;
        this.publicadaEm = agora;
        this.comentadoResultadoId = null;
    }

    public boolean publicada() {
        return jiraIssue != null;
    }

    /** Tira da fila ESTA ocorrência, sem publicar. Uma falha nova (outro resultado) volta a aparecer. */
    public void ignorar(Long resultadoId, Instant agora) {
        this.ignoradoResultadoId = resultadoId;
        this.ignoradoEm = agora;
        this.atualizadaEm = agora; // a falha pode ser ignorada antes de ter qualquer triagem
    }

    public boolean ignorada(Long resultadoId) {
        return resultadoId != null && resultadoId.equals(ignoradoResultadoId);
    }

    /** Registra que esta ocorrência já foi comentada no bug existente. */
    public void registrarComentario(Long resultadoId) {
        this.comentadoResultadoId = resultadoId;
    }

    /**
     * RECORRENTE: o teste já tem bug publicado e voltou a falhar DEPOIS da
     * publicação, numa ocorrência que ainda não foi comentada no bug.
     */
    public boolean recorrente(Long resultadoId, Instant ocorridaEm) {
        return publicada() && publicadaEm != null && ocorridaEm != null && ocorridaEm.isAfter(publicadaEm)
                && !resultadoId.equals(comentadoResultadoId);
    }

    public Triagem(String projetoId, String chaveTeste) {
        this.projetoId = projetoId;
        this.chaveTeste = chaveTeste;
    }

    /** Aplica o rascunho da IA. A classificação fica só como SUGESTÃO até o QA confirmar. */
    public void aplicarRascunho(RascunhoBug r, Long resultadoId, Instant agora) {
        this.resultadoId = resultadoId;
        this.titulo = r.titulo();
        this.classificacaoSugerida = r.classificacao();
        this.severidade = r.severidade();
        this.esperado = r.esperado();
        this.encontrado = r.encontrado();
        this.passos = String.join("\n", r.passos());
        this.analise = r.analise();
        this.origemRascunho = r.origem();
        this.modelo = r.modelo();
        this.atualizadaEm = agora;
    }

    /** Grava a revisão do QA. classificacao null = volta para "pendente". */
    public void revisar(Long resultadoId, Classificacao classificacao, Severidade severidade, String titulo, String esperado,
                        String encontrado, List<String> passos, String observacoes, Instant agora) {
        this.resultadoId = resultadoId;
        this.classificacao = classificacao;
        this.severidade = severidade;
        this.titulo = titulo;
        this.esperado = esperado;
        this.encontrado = encontrado;
        this.passos = passos == null ? null : String.join("\n", passos);
        this.observacoes = observacoes;
        this.atualizadaEm = agora;
    }

    public List<String> getPassos() {
        return passos == null || passos.isBlank() ? List.of() : Arrays.stream(passos.split("\n")).filter(p -> !p.isBlank()).toList();
    }

    public Long getId() { return id; }
    public String getProjetoId() { return projetoId; }
    public String getChaveTeste() { return chaveTeste; }
    public Long getResultadoId() { return resultadoId; }
    public Classificacao getClassificacao() { return classificacao; }
    public Classificacao getClassificacaoSugerida() { return classificacaoSugerida; }
    public Severidade getSeveridade() { return severidade; }
    public String getTitulo() { return titulo; }
    public String getEsperado() { return esperado; }
    public String getEncontrado() { return encontrado; }
    public String getAnalise() { return analise; }
    public String getOrigemRascunho() { return origemRascunho; }
    public String getModelo() { return modelo; }
    public String getObservacoes() { return observacoes; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public String getJiraIssue() { return jiraIssue; }
    public String getJiraUrl() { return jiraUrl; }
    public String getDemanda() { return demanda; }
    public Instant getPublicadaEm() { return publicadaEm; }
    public Long getIgnoradoResultadoId() { return ignoradoResultadoId; }
    public Instant getIgnoradoEm() { return ignoradoEm; }
    public Long getComentadoResultadoId() { return comentadoResultadoId; }
}
