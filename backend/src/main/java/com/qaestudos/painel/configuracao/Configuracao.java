package com.qaestudos.painel.configuracao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** ENTIDADE JPA da tabela {@code configuracao}: um par chave → valor. */
@Entity
@Table(name = "configuracao")
public class Configuracao {

    @Id
    private String chave; // a própria chave é o id (não há número gerado)

    private String valor; // já criptografado quando secreto = true
    private boolean secreto;
    private Instant atualizadoEm;

    protected Configuracao() {}

    public Configuracao(String chave, String valor, boolean secreto, Instant atualizadoEm) {
        this.chave = chave;
        atualizar(valor, secreto, atualizadoEm);
    }

    public void atualizar(String valor, boolean secreto, Instant atualizadoEm) {
        this.valor = valor;
        this.secreto = secreto;
        this.atualizadoEm = atualizadoEm;
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }
    public boolean isSecreto() { return secreto; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
