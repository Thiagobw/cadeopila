package br.com.cadeopila.transactionservice.adapters.out.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Health indicator da MockAPI de contas (resiliência de infraestrutura).
 *
 * <p><b>Problema que resolve:</b> se {@code ACCOUNT_API_BASE_URL} apontar para um token inválido, a
 * aplicação <i>sobe normalmente</i> e só falha em tempo de execução (toda transação vira REJECTED por
 * {@code ACCOUNT_SERVICE_UNAVAILABLE}). Este indicador transforma essa falha silenciosa em um sinal
 * explícito: contribui para {@code /actuator/health} com um ping leve na origem. Se a MockAPI responder
 * 2xx → {@code UP}; se responder 401/403/404 (token/recurso inválido), 5xx ou estourar timeout/conexão →
 * {@code DOWN}, o que leva o {@code /actuator/health} agregado a 503 e o container a {@code unhealthy}.</p>
 *
 * <p>Reusa o mesmo {@code accountRestClient} do {@link AccountRestClientAdapter} (mesma base URL e os
 * mesmos timeouts de conexão/leitura de {@code RestClientConfig}), então o ping herda a blindagem contra
 * lentidão (D5). A URL (que carrega o token) nunca entra nos detalhes nem nos logs — só o código HTTP.</p>
 */
@Component
public class MockApiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MockApiHealthIndicator.class);

    private final RestClient restClient;

    public MockApiHealthIndicator(@Qualifier("accountRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        try {
            // Ping leve: limita a 1 registro e descarta o corpo. Só o status importa aqui.
            restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/accounts")
                            .queryParam("page", 1)
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
            return Health.up().withDetail("account-api", "reachable").build();
        } catch (RestClientResponseException ex) {
            // 4xx/5xx — inclui o 401/403/404 típico de token ou recurso inválido na MockAPI.
            int status = ex.getStatusCode().value();
            log.warn("Health da API de contas DOWN: resposta HTTP {}", status);
            return Health.down().withDetail("account-api", "HTTP " + status).build();
        } catch (RestClientException ex) {
            // Timeout de leitura/conexão, DNS, conexão recusada — sem resposta HTTP.
            log.warn("Health da API de contas DOWN: origem inacessível ({})", ex.getClass().getSimpleName());
            return Health.down().withDetail("account-api", "unreachable").build();
        }
    }
}
