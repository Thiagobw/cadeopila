package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionRequest;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.PagedResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.TransactionResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.UpdateTransactionMetadataRequest;
import br.com.cadeopila.transactionservice.adapters.in.web.mapper.TransactionWebMapper;
import br.com.cadeopila.transactionservice.core.domain.PageQuery;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.DeleteTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.FindTransactionByIdUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.ListTransactionsUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.UpdateTransactionMetadataUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@Tag(name = "Transações", description = "Processamento e consulta de transações financeiras")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final CreateTransactionUseCase createTransaction;
    private final FindTransactionByIdUseCase findTransactionById;
    private final ListTransactionsUseCase listTransactions;
    private final UpdateTransactionMetadataUseCase updateTransactionMetadata;
    private final DeleteTransactionUseCase deleteTransaction;
    private final TransactionWebMapper mapper;

    public TransactionController(CreateTransactionUseCase createTransaction,
                                 FindTransactionByIdUseCase findTransactionById,
                                 ListTransactionsUseCase listTransactions,
                                 UpdateTransactionMetadataUseCase updateTransactionMetadata,
                                 DeleteTransactionUseCase deleteTransaction,
                                 TransactionWebMapper mapper) {
        this.createTransaction = createTransaction;
        this.findTransactionById = findTransactionById;
        this.listTransactions = listTransactions;
        this.updateTransactionMetadata = updateTransactionMetadata;
        this.deleteTransaction = deleteTransaction;
        this.mapper = mapper;
    }

    @Operation(summary = "Processa e cria uma transação",
            description = "Valida a entrada, decide APPROVED/REJECTED e grava. Rejeição de negócio "
                    + "também é gravada e devolve 201 com o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Transação criada (APPROVED ou REJECTED), com header Location"),
            @ApiResponse(responseCode = "400", description = "Entrada inválida (campos ou regra de tipo)"),
            @ApiResponse(responseCode = "500", description = "Erro interno (ex.: falha de persistência)")
    })
    @PostMapping
    public ResponseEntity<CreateTransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request,
                                                            UriComponentsBuilder uriBuilder) {
        var result = createTransaction.create(mapper.toCommand(request));
        var body = mapper.toCreateResponse(result);
        var location = uriBuilder.path("/api/v1/transactions/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @Operation(summary = "Busca uma transação por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação encontrada"),
            @ApiResponse(responseCode = "404", description = "Transação inexistente ou excluída")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(findTransactionById.findById(id)));
    }

    @Operation(summary = "Lista as transações de um usuário (paginado, máx. " + PageQuery.MAX_SIZE + " por página)",
            description = "Filtros opcionais: tipo, status e período (datas inclusivas). Ordem: occurredAt desc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de transações"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos (ex.: período invertido, paginação)")
    })
    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponse>> list(
            @RequestParam UUID userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")
            @PositiveOrZero(message = "deve ser maior ou igual a 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "deve ser maior ou igual a 1")
            @Max(value = PageQuery.MAX_SIZE, message = "deve ser no máximo " + PageQuery.MAX_SIZE) int size) {
        var query = new TransactionQuery(userId, type, status, from, to, new PageQuery(page, size));
        var result = listTransactions.list(query);
        var content = result.content().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, result.totalElements(),
                result.totalPages(), result.pageNumber(), result.pageSize()));
    }

    @Operation(summary = "Edita os metadados (description e category) de uma transação",
            description = "Edição parcial: informe ao menos um campo. Valor, tipo e status são imutáveis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadados atualizados"),
            @ApiResponse(responseCode = "400", description = "Nenhum campo informado ou valor inválido"),
            @ApiResponse(responseCode = "404", description = "Transação inexistente ou excluída"),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência (versão)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateMetadata(@PathVariable UUID id,
                                                             @Valid @RequestBody UpdateTransactionMetadataRequest request) {
        var updated = updateTransactionMetadata.update(mapper.toCommand(id, request));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Operation(summary = "Exclusão lógica de uma transação (apenas REJECTED)",
            description = "Preserva o histórico (deleted_at). Transação APPROVED não pode ser excluída.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Transação excluída logicamente"),
            @ApiResponse(responseCode = "404", description = "Transação inexistente ou já excluída"),
            @ApiResponse(responseCode = "422", description = "Transação APPROVED não pode ser excluída")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteTransaction.delete(id);
        return ResponseEntity.noContent().build();
    }
}
