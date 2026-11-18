package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.Email;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Atualização completa (PUT) do usuário. name e email são obrigatórios; "
        + "password é opcional (informe só para trocar). Só a própria conta pode ser alterada.")
public record UpdateUserRequest(

        @Schema(description = "Nome completo (1 a 100 caracteres)", example = "Ana Souza Lima")
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String name,

        @Schema(description = "E-mail único; normalizado (trim + minúsculas)", example = "ana.souza@email.com")
        @NotBlank(message = "Email é obrigatório")
        @jakarta.validation.constraints.Email(message = "Email deve ser válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @Schema(description = "Nova senha (8 a 72 caracteres). Opcional: omita para manter a atual.",
                example = "NovaSenha@456")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password
) {
    public UpdateUserRequest {
        email = Email.normalize(email);
    }
}
