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

    protected Triagem() {}

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
}
