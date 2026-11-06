package br.com.cadeopila.transactionservice.adapters.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /**
     * Relógio em UTC injetado nos casos de uso, para instantes determinísticos nos testes
     * (nunca {@code Instant.now()}/{@code LocalDateTime.now()} direto no código).
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
