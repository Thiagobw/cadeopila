package br.com.cadeopila.userservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais a validar (uso interno server-to-server: o MS2 repassa o Basic Auth recebido).")
public record ValidateCredentialsRequest(

        @Schema(description = "E-mail do usuário", example = "ana.souza@email.com")
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @Schema(description = "Senha em texto puro (comparada com o hash BCrypt)", example = "Senha@Teste123")
        @NotBlank(message = "password é obrigatório")
        String password
) {}
