package com.qaestudos.painel.triagem;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * ENTIDADE JPA da tabela {@code jira_vinculo}: uma ação do painel no Jira por
 * um teste (bug criado ou comentário de nova ocorrência). É o histórico que
 * diz "este teste já foi reportado" mesmo depois de várias falhas.
 */
@Entity
@Table(name = "jira_vinculo")
public class JiraVinculo {

    public enum Acao { CRIADO, COMENTADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String projetoId;
    private String chaveTeste;
    private Long resultadoId;
    private String jiraIssue;
    private String jiraUrl;

    @Enumerated(EnumType.STRING)
    private Acao acao;

    private Instant criadoEm;

    protected JiraVinculo() {}

    public JiraVinculo(String projetoId, String chaveTeste, Long resultadoId, String jiraIssue, String jiraUrl, Acao acao,
                       Instant criadoEm) {
        this.projetoId = projetoId;
        this.chaveTeste = chaveTeste;
        this.resultadoId = resultadoId;
        this.jiraIssue = jiraIssue;
        this.jiraUrl = jiraUrl;
        this.acao = acao;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public String getProjetoId() { return projetoId; }
    public String getChaveTeste() { return chaveTeste; }
    public Long getResultadoId() { return resultadoId; }
    public String getJiraIssue() { return jiraIssue; }
    public String getJiraUrl() { return jiraUrl; }
    public Acao getAcao() { return acao; }
    public Instant getCriadoEm() { return criadoEm; }
}
