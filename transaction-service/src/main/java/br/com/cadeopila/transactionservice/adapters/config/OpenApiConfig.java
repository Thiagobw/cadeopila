package br.com.cadeopila.transactionservice.adapters.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Nome do esquema de segurança exibido no botão "Authorize" do Swagger UI. */
    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI transactionServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Service API")
                        .description("API de transações financeiras do Cadê o Pila? — "
                                + "depósitos, retiradas, transferências e compras (Arquitetura Hexagonal)")
                        .version("v1"))
                // Habilita o botão "Authorize" (Basic Auth) no Swagger UI. Sem isso, o "Try it out"
                // não envia credenciais e todo endpoint protegido responde 401.
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Usuário = e-mail do cadastro; senha = a senha do usuário "
                                        + "(validada no user-service).")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
