package br.com.cadeopila.transactionservice.adapters.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Configuração da aplicação vinda do ambiente (prefixo {@code app}).
 *
 * <p>As URLs são bases, sem o caminho do recurso: os adapters acrescentam
 * {@code /accounts} e {@code /cambio/v1/cotacao/...}. Eventual barra final é
 * removida no carregamento para não gerar {@code //} no caminho montado.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @NotBlank String timeZone,

        @Valid @NotNull Exchange exchange,

        @Valid @NotNull Account account,

        @Valid @NotNull User user,

        @Valid @NotNull Http http
) {

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public record Exchange(

            @NotEmpty List<String> currencies,

            @NotBlank String baseUrl
    ) {
        public Exchange {
            baseUrl = stripTrailingSlash(baseUrl);
        }
    }

    public record Account(

            @NotBlank(message = "A URL base da API de contas (ACCOUNT_API_BASE_URL) é obrigatória")
            String baseUrl
    ) {
        public Account {
            baseUrl = stripTrailingSlash(baseUrl);
        }
    }

    public record User(

            @NotBlank(message = "A URL base do user-service (USER_API_BASE_URL) é obrigatória")
            String baseUrl
    ) {
        public User {
            baseUrl = stripTrailingSlash(baseUrl);
        }
    }

    public record Http(

            @NotNull Duration connectTimeout,

            @NotNull Duration readTimeout
    ) {}

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        var trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
