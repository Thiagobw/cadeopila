package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @jakarta.validation.constraints.Email(message = "Email deve ser válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String password
) {
    public UpdateUserRequest {
        email = Email.normalize(email);
    }
}
