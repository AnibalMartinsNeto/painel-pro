package com.qaestudos.painel.triagem;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TriagemRepository extends JpaRepository<Triagem, Long> {

    Optional<Triagem> findByProjetoIdAndChaveTeste(String projetoId, String chaveTeste);

    List<Triagem> findByProjetoIdAndChaveTesteIn(String projetoId, Collection<String> chaves);

    /** A triagem que gerou o bug (ex.: DEV-8), se ele foi publicado pelo painel. */
    Optional<Triagem> findByProjetoIdAndJiraIssue(String projetoId, String jiraIssue);

    /**
     * Reserva a publicação no Jira de forma ATÔMICA: só marca se não houver
     * outra publicação em andamento (ou se a reserva anterior ficou velha, ex.:
     * o backend caiu no meio). Devolve 1 se reservou, 0 se já estava reservada.
     */
    @Modifying
    @Query("""
            update Triagem t set t.publicandoEm = :agora
            where t.id = :id and (t.publicandoEm is null or t.publicandoEm < :expirada)
            """)
    int reservarPublicacao(@Param("id") Long id, @Param("agora") Instant agora, @Param("expirada") Instant expirada);

    @Modifying
    @Query("update Triagem t set t.publicandoEm = null where t.id = :id")
    void liberarPublicacao(@Param("id") Long id);

    /** Bugs já publicados no Jira pelo painel, mais recentes primeiro. */
    List<Triagem> findByProjetoIdAndJiraIssueIsNotNullOrderByPublicadaEmDesc(String projetoId);

    /**
     * Fila de triagem: para cada teste do projeto, pega a ocorrência MAIS
     * RECENTE e mantém só as que falharam — se o teste voltou a passar, ele
     * sai da fila sozinho.
     *
     * <p>Consulta NATIVA (SQL do PostgreSQL, não JPQL): o
     * {@code DISTINCT ON (r.chave)} com {@code ORDER BY ... DESC} devolve uma
     * linha por chave, a primeira da ordenação — ou seja, a mais nova. Os
     * apelidos entre aspas preservam maiúsculas para casar com a projeção.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT DISTINCT ON (r.chave)
                       r.id            AS "resultadoId",
                       e.id            AS "execucaoId",
                       r.spec          AS "spec",
                       r.titulo        AS "titulo",
                       r.chave         AS "chave",
                       r.status        AS "status",
                       r.mensagem_erro AS "mensagemErro",
                       r.tipo_erro     AS "tipoErro",
                       e.iniciada_em   AS "ocorridaEm",
                       e.navegador     AS "navegador"
                FROM resultado_teste r
                JOIN execucao e ON e.id = r.execucao_id
                WHERE e.projeto_id = :projetoId
                  AND NOT e.dev
                  AND e.status IN ('PASSOU', 'FALHOU')
                ORDER BY r.chave, e.iniciada_em DESC, r.id DESC
            ) ultima
            WHERE ultima."status" = 'FALHOU'
            ORDER BY ultima."ocorridaEm" DESC
            """)
    List<FalhaEmAberto> listarFalhasEmAberto(@Param("projetoId") String projetoId);

    /** Projeção por interface: o Spring implementa os getters a partir das colunas da consulta. */
    interface FalhaEmAberto {
        Long getResultadoId();
        Long getExecucaoId();
        String getSpec();
        String getTitulo();
        String getChave();
        String getMensagemErro();
        String getTipoErro();
        Instant getOcorridaEm();
        String getNavegador();
    }
}
