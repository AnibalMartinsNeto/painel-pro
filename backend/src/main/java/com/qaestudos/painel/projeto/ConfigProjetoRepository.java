package com.qaestudos.painel.projeto;

import com.qaestudos.painel.config.PainelProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;

/** Implementação que lê os projetos do bloco {@code painel.projetos} do application.yml. */
@Repository
public class ConfigProjetoRepository implements ProjetoRepository {

    private final List<Projeto> projetos;

    // Injeção de dependência pelo construtor: o Spring cria PainelProperties
    // e entrega aqui. A classe não precisa saber como ler o YAML.
    public ConfigProjetoRepository(PainelProperties properties) {
        this.projetos = properties.projetos().stream()
                .map(c -> new Projeto(
                        c.id(),
                        c.nome(),
                        c.tipo(),
                        Path.of(c.diretorio()).toAbsolutePath().normalize(),
                        c.pastaSpecs(),
                        Pattern.compile(c.padraoSpec()),
                        c.navegadores()))
                .toList();
    }

    @Override
    public List<Projeto> findAll() {
        return projetos;
    }

    @Override
    public Optional<Projeto> findById(String id) {
        return projetos.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
