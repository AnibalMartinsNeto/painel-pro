package com.qaestudos.painel.config;

import com.qaestudos.painel.projeto.TipoProjeto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Espelho tipado do bloco {@code painel:} do application.yml.
 *
 * <p>O Spring converte o YAML nestes records na inicialização. Com
 * {@code @Validated}, uma configuração inválida (ex.: projeto sem id)
 * impede a aplicação de subir — o erro aparece na hora, não em produção.
 */
@Validated
@ConfigurationProperties(prefix = "painel")
public record PainelProperties(@NotEmpty List<@Valid ProjetoConfig> projetos) {

    public record ProjetoConfig(
            @NotBlank String id,
            @NotBlank String nome,
            @NotNull TipoProjeto tipo,
            // String, e não Path: o conversor do Spring trata Path como
            // "recurso" e rejeita caminhos relativos como "../../Cypress".
            @NotBlank String diretorio,
            @NotBlank String pastaSpecs,
            @NotBlank String padraoSpec,
            List<String> navegadores) {

        public ProjetoConfig {
            navegadores = navegadores == null ? List.of() : List.copyOf(navegadores);
        }
    }
}
