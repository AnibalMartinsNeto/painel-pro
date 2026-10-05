package com.qaestudos.painel.execucao;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acesso ao banco para execuções — só uma INTERFACE. O Spring Data cria a
 * implementação na inicialização:
 *
 * <ul>
 *   <li>Herdados de JpaRepository: save, findById, findAll, delete, count...
 *   <li>Derivados do NOME do método: {@code findTop50ByProjetoIdOrderByIniciadaEmDesc}
 *       vira {@code SELECT ... WHERE projeto_id = ? ORDER BY iniciada_em DESC LIMIT 50}.
 *   <li>Com {@code @Query}: JPQL escrito à mão, para consultas que o nome não expressa.
 * </ul>
 */
public interface ExecucaoRepository extends JpaRepository<Execucao, Long> {

    List<Execucao> findTop50ByProjetoIdOrderByIniciadaEmDesc(String projetoId);

    Optional<Execucao> findFirstByProjetoIdAndStatusInOrderByIniciadaEmDesc(String projetoId, Collection<StatusExecucao> status);

    Optional<Execucao> findFirstByProjetoIdAndScriptOrderByIniciadaEmDesc(String projetoId, String script);

    boolean existsByOrigem(String origem);

    List<Execucao> findByStatus(StatusExecucao status);

    /** Execução com os resultados, numa única consulta (JOIN FETCH evita o problema N+1). */
    @Query("select e from Execucao e left join fetch e.resultados where e.id = :id")
    Optional<Execucao> buscarComResultados(@Param("id") Long id);

    /** Totais do período, agregados pelo próprio banco (SUM/COUNT), sem carregar linhas. */
    @Query("""
            select new com.qaestudos.painel.execucao.ResumoPeriodo(
                count(e), coalesce(sum(e.total), 0L), coalesce(sum(e.aprovados), 0L), coalesce(sum(e.reprovados), 0L))
            from Execucao e
            where e.projetoId = :projetoId
              and e.iniciadaEm >= :desde
              and e.status in (com.qaestudos.painel.execucao.StatusExecucao.PASSOU,
                               com.qaestudos.painel.execucao.StatusExecucao.FALHOU)
            """)
    ResumoPeriodo resumirPeriodo(@Param("projetoId") String projetoId, @Param("desde") Instant desde);

    /** Quantidade de falhas por spec no período (base do gráfico "falhas por módulo"). */
    @Query("""
            select new com.qaestudos.painel.execucao.FalhasPorSpec(r.spec, count(r))
            from ResultadoTeste r
            where r.execucao.projetoId = :projetoId
              and r.execucao.iniciadaEm >= :desde
              and r.status = com.qaestudos.painel.execucao.StatusTeste.FALHOU
            group by r.spec
            order by count(r) desc
            """)
    List<FalhasPorSpec> contarFalhasPorSpec(@Param("projetoId") String projetoId, @Param("desde") Instant desde);

    /** Últimas execuções concluídas (base do gráfico "aprovação por execução"). */
    List<Execucao> findTop24ByProjetoIdAndStatusInOrderByIniciadaEmDesc(String projetoId, Collection<StatusExecucao> status);

    /** Todas as execuções do projeto, da mais recente para a mais antiga (exportação CSV). */
    List<Execucao> findByProjetoIdOrderByIniciadaEmDesc(String projetoId);

    long countByProjetoId(String projetoId);

    @Query("select coalesce(sum(e.duracaoMs), 0L) from Execucao e where e.projetoId = :projetoId")
    long somarDuracao(@Param("projetoId") String projetoId);
}
