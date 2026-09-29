package com.qaestudos.painel;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Banco dos TESTES: um PostgreSQL descartável num container, criado pelo
 * Testcontainers e destruído no fim da execução.
 *
 * <p>{@code @ServiceConnection} faz o Spring apontar o datasource para esse
 * container automaticamente. Os testes nunca tocam no banco de
 * desenvolvimento, e rodam contra o MESMO PostgreSQL da produção (não um
 * banco "parecido" em memória), então o SQL testado é o SQL real.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
