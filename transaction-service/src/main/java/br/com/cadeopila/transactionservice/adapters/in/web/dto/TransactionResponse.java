package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Representação completa de uma transação")
public record TransactionResponse(

        @Schema(example = "a1b2c3d4-58cc-4372-a567-0e02b2c3d479") UUID id,
        @Schema(example = "f47ac10b-58cc-4372-a567-0e02b2c3d479") UUID userId,
        @Schema(description = "Preenchido apenas em TRANSFER", example = "null") UUID destinationUserId,
        @Schema(example = "PURCHASE") TransactionType type,
        @Schema(example = "150.00") BigDecimal amount,
        @Schema(example = "Mercado do mês") String description,
        @Schema(example = "FOOD") TransactionCategory category,
        @Schema(example = "APPROVED") TransactionStatus status,
        @Schema(description = "Motivo quando REJECTED; null quando APPROVED", example = "null") RejectionReason rejectionReason,
        @Schema(example = "2026-11-06T18:00:00Z") Instant occurredAt,
        @Schema(example = "2026-11-06T18:00:00Z") Instant createdAt,
        @Schema(example = "2026-11-06T18:00:00Z") Instant updatedAt,
        ExchangeResponse exchange
) {}
