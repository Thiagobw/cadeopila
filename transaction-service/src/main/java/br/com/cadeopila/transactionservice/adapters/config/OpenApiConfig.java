package br.com.cadeopila.transactionservice.adapters.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transaction Service API")
                .description("API de transações financeiras do Cadê o Pila? — "
                        + "depósitos, retiradas, transferências e compras (Arquitetura Hexagonal)")
                .version("v1"));
    }
}
