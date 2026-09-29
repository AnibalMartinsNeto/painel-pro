package com.qaestudos.painel.execucao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultadoTesteRepository extends JpaRepository<ResultadoTeste, Long> {

    /** Resultado já com a execução carregada (a relação é LAZY). */
    @Query("select r from ResultadoTeste r join fetch r.execucao where r.id = :id")
    Optional<ResultadoTeste> buscarComExecucao(@Param("id") Long id);
}
