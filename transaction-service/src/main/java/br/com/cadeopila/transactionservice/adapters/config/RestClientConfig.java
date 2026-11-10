package br.com.cadeopila.transactionservice.adapters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP das integrações externas, com timeouts de conexão/leitura e sem retry.
 * Cada cliente recebe sua URL base; os adapters acrescentam o caminho do recurso.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public ClientHttpRequestFactory transactionHttpRequestFactory(AppProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.http().connectTimeout().toMillis());
        factory.setReadTimeout((int) props.http().readTimeout().toMillis());
        return factory;
    }

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                        ClientHttpRequestFactory transactionHttpRequestFactory,
                                        AppProperties props) {
        return builder
                .baseUrl(props.account().baseUrl())
                .requestFactory(transactionHttpRequestFactory)
                .build();
    }

    @Bean
    public RestClient exchangeRestClient(RestClient.Builder builder,
                                         ClientHttpRequestFactory transactionHttpRequestFactory,
                                         AppProperties props) {
        return builder
                .baseUrl(props.exchange().baseUrl())
                .requestFactory(transactionHttpRequestFactory)
                .build();
    }

    @Bean
    public RestClient userRestClient(RestClient.Builder builder,
                                     ClientHttpRequestFactory transactionHttpRequestFactory,
                                     AppProperties props) {
        return builder
                .baseUrl(props.user().baseUrl())
                .requestFactory(transactionHttpRequestFactory)
                .build();
    }
}
