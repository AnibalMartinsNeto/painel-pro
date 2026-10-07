package com.qaestudos.painel.execucao;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {

    /** Evidências de uma execução inteira (tela de detalhe). */
    List<Evidencia> findByResultadoExecucaoIdOrderByIdAsc(Long execucaoId);

    /** Evidências de vários resultados de uma vez (fila de triagem). */
    List<Evidencia> findByResultadoIdInOrderByIdAsc(Collection<Long> resultadoIds);
}
