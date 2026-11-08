package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resultado da criação da transação")
public record CreateTransactionResponse(

        @Schema(example = "a1b2c3d4-58cc-4372-a567-0e02b2c3d479") UUID id,
        @Schema(example = "APPROVED") TransactionStatus status,
        @Schema(description = "Motivo quando REJECTED; null quando APPROVED", example = "null")
        RejectionReason rejectionReason,
        ExchangeResponse exchange,
        AccountResponse account
) {}
