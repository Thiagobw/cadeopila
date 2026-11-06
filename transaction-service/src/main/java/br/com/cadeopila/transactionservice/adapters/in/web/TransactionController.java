package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionRequest;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.mapper.TransactionWebMapper;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
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
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Transações", description = "Processamento e consulta de transações financeiras")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final CreateTransactionUseCase createTransaction;
    private final TransactionWebMapper mapper;

    public TransactionController(CreateTransactionUseCase createTransaction, TransactionWebMapper mapper) {
        this.createTransaction = createTransaction;
        this.mapper = mapper;
    }

    @Operation(summary = "Processa e cria uma transação",
            description = "Valida a entrada, decide APPROVED/REJECTED e grava. Rejeição de negócio "
                    + "também é gravada e devolve 201 com o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Transação criada (APPROVED ou REJECTED), com header Location"),
            @ApiResponse(responseCode = "400", description = "Entrada inválida (campos ou regra de tipo)"),
            @ApiResponse(responseCode = "500", description = "Erro interno (ex.: falha de persistência compensada)")
    })
    @PostMapping
    public ResponseEntity<CreateTransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request,
                                                            UriComponentsBuilder uriBuilder) {
        var result = createTransaction.create(mapper.toCommand(request));
        var body = mapper.toCreateResponse(result);
        var location = uriBuilder.path("/api/v1/transactions/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }
}
