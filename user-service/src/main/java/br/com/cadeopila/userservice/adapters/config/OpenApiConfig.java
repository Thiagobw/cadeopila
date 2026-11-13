package br.com.cadeopila.userservice.adapters.config;

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
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("API de usuários do Cadê o Pila? — CRUD, import em massa via CSV "
                                + "e validação de credenciais (Arquitetura Hexagonal)")
                        .version("v1"))
                // Habilita o botão "Authorize" (Basic Auth) no Swagger UI. O cadastro (POST /users e
                // /users/batch) é público; listar, buscar, atualizar e excluir exigem credenciais —
                // sem este esquema, o "Try it out" dessas rotas responde 401.
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Usuário = e-mail do cadastro; senha = a senha do usuário.")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
