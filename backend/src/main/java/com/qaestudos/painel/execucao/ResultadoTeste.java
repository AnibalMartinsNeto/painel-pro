package com.qaestudos.painel.execucao;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** ENTIDADE JPA da tabela {@code resultado_teste}: um teste dentro de uma execução. */
@Entity
@Table(name = "resultado_teste")
public class ResultadoTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Muitos-para-um: lado "dono" da chave estrangeira execucao_id.
    // LAZY = a execução só é carregada do banco se alguém acessá-la.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id")
    private Execucao execucao;

    private String spec;
    private String titulo;
    private String chave;

    @Enumerated(EnumType.STRING)
    private StatusTeste status;

    private Long duracaoMs;
    private String mensagemErro;
    private String tipoErro;

    protected ResultadoTeste() {}

    public ResultadoTeste(String spec, String titulo, StatusTeste status, Long duracaoMs, String mensagemErro, String tipoErro) {
        this.spec = spec;
        this.titulo = titulo;
        this.chave = spec + " › " + titulo;
        this.status = status;
        this.duracaoMs = duracaoMs;
        this.mensagemErro = mensagemErro;
        this.tipoErro = tipoErro;
    }

    void vincular(Execucao execucao) {
        this.execucao = execucao;
    }

    public Long getId() { return id; }
    public Execucao getExecucao() { return execucao; }
    public String getSpec() { return spec; }
    public String getTitulo() { return titulo; }
    public String getChave() { return chave; }
    public StatusTeste getStatus() { return status; }
    public Long getDuracaoMs() { return duracaoMs; }
    public String getMensagemErro() { return mensagemErro; }
    public String getTipoErro() { return tipoErro; }
}
