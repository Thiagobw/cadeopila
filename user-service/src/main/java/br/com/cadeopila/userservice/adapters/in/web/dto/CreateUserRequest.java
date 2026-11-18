package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.Email;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastro de um novo usuário (rota pública).")
public record CreateUserRequest(

        @Schema(description = "Nome completo (1 a 100 caracteres)", example = "Ana Souza")
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String name,

        @Schema(description = "E-mail único; é normalizado (trim + minúsculas) e vira o login do Basic Auth",
                example = "ana.souza@email.com")
        @NotBlank(message = "Email é obrigatório")
        @jakarta.validation.constraints.Email(message = "Email deve ser válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @Schema(description = "CPF com exatamente 11 dígitos numéricos, sem pontuação; único",
                example = "11122233344")
        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve conter exatamente 11 dígitos numéricos")
        String cpf,

        @Schema(description = "Senha (8 a 72 caracteres); armazenada como hash BCrypt, nunca devolvida",
                example = "Senha@Teste123")
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password
) {
    public CreateUserRequest {
        email = Email.normalize(email);
    }
}
