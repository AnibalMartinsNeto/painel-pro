package com.qaestudos.painel.execucao;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * ENTIDADE JPA da tabela {@code evidencia}: um arquivo (screenshot, trace)
 * de um teste. O arquivo em si fica na pasta de evidências do painel
 * ({@link ArmazemEvidencias}); aqui ficam o tipo e o caminho relativo.
 */
@Entity
@Table(name = "evidencia")
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resultado_id")
    private ResultadoTeste resultado;

    private String nome;
    private String tipo;
    private String arquivo;
    private long tamanho;

    protected Evidencia() {}

    public Evidencia(String nome, String tipo, String arquivo, long tamanho) {
        this.nome = nome;
        this.tipo = tipo;
        this.arquivo = arquivo;
        this.tamanho = tamanho;
    }

    void vincular(ResultadoTeste resultado) {
        this.resultado = resultado;
    }

    public boolean imagem() {
        return tipo != null && tipo.startsWith("image/");
    }

    public Long getId() { return id; }
    public ResultadoTeste getResultado() { return resultado; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public String getArquivo() { return arquivo; }
    public long getTamanho() { return tamanho; }
}
