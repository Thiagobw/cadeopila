package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.adapters.in.web.dto.AuthenticatedUserResponse;
import br.com.cadeopila.userservice.adapters.in.web.dto.ValidateCredentialsRequest;
import br.com.cadeopila.userservice.core.ports.in.ValidateCredentialsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Validação de credenciais para os demais serviços")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ValidateCredentialsUseCase validateCredentials;

    public AuthController(ValidateCredentialsUseCase validateCredentials) {
        this.validateCredentials = validateCredentials;
    }

    @Operation(summary = "Valida e-mail e senha e devolve o id do usuário",
            description = "Endpoint server-to-server: o transaction-service repassa o Basic Auth recebido. "
                    + "Credenciais válidas → 200 com o userId; inválidas → 401 (sem distinguir e-mail de senha).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciais válidas"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido (e-mail/senha ausentes ou malformados)"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    @PostMapping("/validate")
    public ResponseEntity<AuthenticatedUserResponse> validate(@Valid @RequestBody ValidateCredentialsRequest request) {
        var user = validateCredentials.validate(
                new ValidateCredentialsUseCase.Command(request.email(), request.password()));
        return ResponseEntity.ok(new AuthenticatedUserResponse(user.getId()));
    }
}
