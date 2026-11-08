package br.com.cadeopila.transactionservice.adapters.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

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

    /**
     * Fuso da aplicação (de {@code app.time-zone}), exposto como tipo do JDK para que os casos de
     * uso (camada application) não precisem conhecer {@link AppProperties} (que vive nos adapters).
     */
    @Bean
    public ZoneId appZoneId(AppProperties properties) {
        return properties.zoneId();
    }
}
