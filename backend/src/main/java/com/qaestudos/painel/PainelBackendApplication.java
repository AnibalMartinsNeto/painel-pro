package com.qaestudos.painel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada da aplicação.
 *
 * <p>{@code @SpringBootApplication} liga três coisas: configuração
 * automática, varredura de componentes (acha @RestController, @Service,
 * @Repository neste pacote e nos filhos) e esta classe como configuração.
 * {@code @ConfigurationPropertiesScan} registra os records de configuração
 * como {@link com.qaestudos.painel.config.PainelProperties}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PainelBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PainelBackendApplication.class, args);
    }
}
