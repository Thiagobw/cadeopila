package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.AccountBalanceResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.mapper.AccountWebMapper;
import br.com.cadeopila.transactionservice.core.ports.in.GetAccountBalanceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Contas", description = "Saldo atual e limites da conta")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final GetAccountBalanceUseCase getAccountBalance;
    private final AccountWebMapper mapper;

    public AccountController(GetAccountBalanceUseCase getAccountBalance, AccountWebMapper mapper) {
        this.getAccountBalance = getAccountBalance;
        this.mapper = mapper;
    }

    @Operation(summary = "Consulta o saldo atual e os limites da conta do usuário autenticado",
            description = "Saldo atual = saldo inicial da Mock + histórico aprovado do MS2. Limites vêm da Mock. "
                    + "O usuário é o autenticado via Basic Auth.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo e limites"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Conta inexistente para o usuário"),
            @ApiResponse(responseCode = "503", description = "API de contas indisponível")
    })
    @GetMapping("/balance")
    public ResponseEntity<AccountBalanceResponse> balance(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(mapper.toResponse(getAccountBalance.get(userId)));
    }
}
