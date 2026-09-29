package com.qaestudos.painel.configuracao;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à tabela configuracao. Os métodos herdados (findById, save, deleteById) bastam. */
public interface ConfiguracaoRepository extends JpaRepository<Configuracao, String> {}
