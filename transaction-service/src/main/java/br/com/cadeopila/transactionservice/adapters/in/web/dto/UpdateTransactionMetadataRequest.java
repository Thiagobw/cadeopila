package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Edição de metadados; informe ao menos um campo. Só description e category são editáveis.")
public record UpdateTransactionMetadataRequest(

        @Schema(description = "Nova descrição (1 a 255 caracteres)", example = "Mercado do mês (corrigido)")
        @Size(max = 255, message = "description deve ter no máximo 255 caracteres")
        String description,

        @Schema(description = "Nova categoria", example = "FOOD")
        TransactionCategory category
) {}
