package com.qaestudos.painel.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O "agora" da aplicação vem de um Clock injetado, e não de Instant.now()
 * espalhado pelo código. Nos testes, basta trocar por um relógio fixo para
 * testar regras que dependem de data (ex.: "execuções deste mês").
 */
@Configuration
public class RelogioConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
