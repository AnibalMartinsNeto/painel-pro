package com.qaestudos.painel.projeto;

import java.util.List;
import java.util.Optional;

/**
 * Camada de acesso a dados dos projetos.
 *
 * <p>É uma interface de propósito: o Service depende só deste contrato e
 * não sabe de onde os projetos vêm. Hoje vêm do application.yml
 * ({@link ConfigProjetoRepository}); poderiam vir do banco sem que o
 * Service ou o Controller mudassem uma linha.
 */
public interface ProjetoRepository {

    List<Projeto> findAll();

    Optional<Projeto> findById(String id);
}
