package com.qaestudos.painel.execucao;

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
            where r.execucao.projetoId = :projetoId
            group by r.chave, r.spec, r.titulo
            """)
    List<HistoricoTeste> historicoPorTeste(@Param("projetoId") String projetoId);

    /** Falhas do projeto, da mais recente para a mais antiga, já com a execução carregada. */
    @Query("""
            select r from ResultadoTeste r join fetch r.execucao e
            where e.projetoId = :projetoId and r.status = com.qaestudos.painel.execucao.StatusTeste.FALHOU
            order by e.iniciadaEm desc, r.id desc
            """)
    List<ResultadoTeste> falhasRecentes(@Param("projetoId") String projetoId);
}
