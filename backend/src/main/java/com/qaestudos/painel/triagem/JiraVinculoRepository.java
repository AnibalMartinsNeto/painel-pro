package com.qaestudos.painel.triagem;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraVinculoRepository extends JpaRepository<JiraVinculo, Long> {

    /** Histórico dos testes da fila, numa consulta só (mais recente primeiro). */
    List<JiraVinculo> findByProjetoIdAndChaveTesteInOrderByCriadoEmDesc(String projetoId, Collection<String> chaves);
}
