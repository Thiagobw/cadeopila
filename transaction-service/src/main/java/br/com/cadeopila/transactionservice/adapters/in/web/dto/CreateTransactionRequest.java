package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Solicitação de criação de transação")
public record CreateTransactionRequest(

        @Schema(description = "Usuário dono da transação", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        @NotNull(message = "userId é obrigatório")
        UUID userId,

        @Schema(description = "Tipo da transação", example = "PURCHASE")
        @NotNull(message = "type é obrigatório")
        TransactionType type,

        @Schema(description = "Valor em BRL, maior que zero, até 2 casas decimais", example = "150.00")
        @NotNull(message = "amount é obrigatório")
        @Positive(message = "amount deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "amount deve ter no máximo 2 casas decimais")
        BigDecimal amount,

        @Schema(description = "Descrição da transação (1 a 255 caracteres)", example = "Mercado do mês")
        @NotBlank(message = "description é obrigatória")
        @Size(max = 255, message = "description deve ter no máximo 255 caracteres")
        String description,

        @Schema(description = "Categoria (obrigatória em PURCHASE; nos demais tipos assume OTHER)", example = "FOOD")
        TransactionCategory category,

        @Schema(description = "Usuário de destino (obrigatório e exclusivo de TRANSFER)",
                example = "9f1c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8")
        UUID destinationUserId,

        @Schema(description = "Grau de necessidade (nível 1). Opcional: se ausente, é derivado da categoria. "
                + "Valores: ESSENCIAL, ESTILO_DE_VIDA, SAUDE_FINANCEIRA, DIVIDAS_IMPREVISTOS",
                example = "ESTILO_DE_VIDA")
        CategoryLevel1 categoryLevel1,

        @Schema(description = "Subcategoria livre (nível 2), ex.: Delivery, Aluguel. Usar \"OUTROS\" como "
                + "catch-all exige description com no mínimo 5 caracteres (regra de fricção).",
                example = "Delivery")
        @Size(max = 100, message = "categoryLevel2 deve ter no máximo 100 caracteres")
        String categoryLevel2,

        @Schema(description = "Despesa fixa — entra no Custo Fixo Operacional (padrão false)", example = "false")
        @JsonProperty("isFixed")
        Boolean isFixed,

        @Schema(description = "Despesa recorrente (padrão false)", example = "false")
        @JsonProperty("isRecurring")
        Boolean isRecurring
) {}
