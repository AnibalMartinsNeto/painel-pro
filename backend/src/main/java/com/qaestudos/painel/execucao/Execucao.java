package com.qaestudos.painel.execucao;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ENTIDADE JPA: esta classe é o espelho da tabela {@code execucao}.
 *
 * <p>Cada campo corresponde a uma coluna (o Hibernate converte camelCase
 * para snake_case: {@code projetoId} → {@code projeto_id}). Ao salvar um
 * objeto, o Hibernate gera o INSERT; ao buscar, gera o SELECT e monta o
 * objeto. Com {@code ddl-auto: validate}, se esta classe e a tabela
 * divergirem, a aplicação se recusa a subir.
 *
 * <p>Diferente dos DTOs, entidades não são records: o Hibernate precisa
 * de um construtor vazio e de campos que ele possa preencher.
 */
@Entity
@Table(name = "execucao")
public class Execucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // o banco gera o id (GENERATED ALWAYS AS IDENTITY)
    private Long id;

    private String projetoId;
    private String script;
    private String navegador;

    @Enumerated(EnumType.STRING) // grava "PASSOU", não 1 — sobrevive a reordenar o enum
    private StatusExecucao status;

    private Instant iniciadaEm;
    private Instant finalizadaEm;
    private int total;
    private int aprovados;
    private int reprovados;
    private int pulados;
    private Long duracaoMs;
    private String versaoFerramenta;
    private String erro;

    @Column(unique = true)
    private String origem;

    private String log;
    private boolean dev; // execução de teste: fora das métricas (ver V7)

    // Um-para-muitos: uma execução tem vários resultados. cascade=ALL faz o
    // save da execução salvar também os resultados; orphanRemoval apaga o
    // resultado removido da lista.
    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ResultadoTeste> resultados = new ArrayList<>();

    /** Exigido pelo JPA; não use no código. */
    protected Execucao() {}

    public Execucao(String projetoId, String script, String navegador, Instant iniciadaEm) {
        this.projetoId = projetoId;
        this.script = script;
        this.navegador = navegador;
        this.iniciadaEm = iniciadaEm;
        this.status = StatusExecucao.EM_ANDAMENTO;
    }

    /** Execução de teste ("dev"): fica no histórico, mas fora das métricas, relatórios e triagem. */
    public void marcarComoDev() {
        this.dev = true;
    }

    /** Adiciona um resultado mantendo os dois lados da relação coerentes. */
    public void adicionarResultado(ResultadoTeste resultado) {
        resultado.vincular(this);
        resultados.add(resultado);
    }

    /**
     * Encerra a execução: define o status e recalcula os totais a partir dos
     * resultados — os contadores nunca ficam divergentes da lista.
     */
    public void finalizar(StatusExecucao status, Instant finalizadaEm, Long duracaoMs) {
        this.status = status;
        this.finalizadaEm = finalizadaEm;
        this.duracaoMs = duracaoMs;
        this.total = resultados.size();
        this.aprovados = contar(StatusTeste.PASSOU);
        this.reprovados = contar(StatusTeste.FALHOU);
        this.pulados = total - aprovados - reprovados;
    }

    private int contar(StatusTeste s) {
        return (int) resultados.stream().filter(r -> r.getStatus() == s).count();
    }

    public void registrarErro(String erro) {
        this.erro = erro;
    }

    public void definirVersaoFerramenta(String versao) {
        this.versaoFerramenta = versao;
    }

    public void definirOrigem(String origem) {
        this.origem = origem;
    }

    /** Limite de ~1 milhão de caracteres: guarda o FIM do log, onde ficam o resumo e os erros. */
    public void registrarLog(String log) {
        int limite = 1_000_000;
        this.log = log == null || log.length() <= limite ? log : "[... início do log omitido ...]\n" + log.substring(log.length() - limite);
    }

    public Long getId() { return id; }
    public String getProjetoId() { return projetoId; }
    public String getScript() { return script; }
    public String getNavegador() { return navegador; }
    public StatusExecucao getStatus() { return status; }
    public Instant getIniciadaEm() { return iniciadaEm; }
    public Instant getFinalizadaEm() { return finalizadaEm; }
    public int getTotal() { return total; }
    public int getAprovados() { return aprovados; }
    public int getReprovados() { return reprovados; }
    public int getPulados() { return pulados; }
    public Long getDuracaoMs() { return duracaoMs; }
    public String getVersaoFerramenta() { return versaoFerramenta; }
    public String getErro() { return erro; }
    public String getOrigem() { return origem; }
    public String getLog() { return log; }
    public boolean isDev() { return dev; }
    public List<ResultadoTeste> getResultados() { return Collections.unmodifiableList(resultados); }
}
