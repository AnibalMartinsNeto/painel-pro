package com.qaestudos.painel.execucao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultadoTesteRepository extends JpaRepository<ResultadoTeste, Long> {

    /** Resultado já com a execução carregada (a relação é LAZY). */
    @Query("select r from ResultadoTeste r join fetch r.execucao where r.id = :id")
    Optional<ResultadoTeste> buscarComExecucao(@Param("id") Long id);

    /**
     * Histórico de cada teste do projeto, agregado pelo banco: em quantas
     * execuções apareceu, quantas vezes falhou e quantas passou.
     */
    @Query("""
            select new com.qaestudos.painel.execucao.HistoricoTeste(
                r.chave, r.spec, r.titulo, count(r),
                sum(case when r.status = com.qaestudos.painel.execucao.StatusTeste.FALHOU then 1L else 0L end),
                sum(case when r.status = com.qaestudos.painel.execucao.StatusTeste.PASSOU then 1L else 0L end))
            from ResultadoTeste r
            where r.execucao.projetoId = :projetoId and r.execucao.dev = false
            group by r.chave, r.spec, r.titulo
            """)
    List<HistoricoTeste> historicoPorTeste(@Param("projetoId") String projetoId);

    /**
     * Os resultados da execução real MAIS RECENTE de cada spec informado: a
     * base do relatório de validação da demanda. Pela execução (e não "o
     * último de cada teste") para que testes removidos ou renomeados no spec
     * não apareçam com resultados antigos.
     */
    @Query(nativeQuery = true, value = """
            WITH ultima AS (
                SELECT DISTINCT ON (r.spec) r.spec, e.id AS execucao_id
                FROM resultado_teste r
                JOIN execucao e ON e.id = r.execucao_id
                WHERE e.projeto_id = :projetoId AND NOT e.dev AND e.status IN ('PASSOU', 'FALHOU') AND r.spec IN (:specs)
                ORDER BY r.spec, e.iniciada_em DESC, e.id DESC
            )
            SELECT r.spec          AS "spec",
                   r.titulo        AS "titulo",
                   r.status        AS "status",
                   r.mensagem_erro AS "mensagemErro",
                   e.id            AS "execucaoId",
                   e.iniciada_em   AS "quando",
                   e.navegador     AS "navegador"
            FROM ultima u
            JOIN resultado_teste r ON r.execucao_id = u.execucao_id AND r.spec = u.spec
            JOIN execucao e ON e.id = r.execucao_id
            ORDER BY r.spec, r.id
            """)
    List<UltimoResultado> ultimosResultados(@Param("projetoId") String projetoId, @Param("specs") Collection<String> specs);

    interface UltimoResultado {
        String getSpec();
        String getTitulo();
        String getStatus();
        String getMensagemErro();
        Long getExecucaoId();
        java.time.Instant getQuando();
        String getNavegador();
    }

    /** Falhas do projeto, da mais recente para a mais antiga, já com a execução carregada. */
    @Query("""
            select r from ResultadoTeste r join fetch r.execucao e
            where e.projetoId = :projetoId and e.dev = false and r.status = com.qaestudos.painel.execucao.StatusTeste.FALHOU
            order by e.iniciadaEm desc, r.id desc
            """)
    List<ResultadoTeste> falhasRecentes(@Param("projetoId") String projetoId);
}
