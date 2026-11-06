package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Dados de uma nova transação já validados quanto às invariantes de domínio que dependem
 * do tipo (regras de formato de campo ficam na Bean Validation do DTO). É construído antes
 * de qualquer chamada externa: uma entrada inválida falha sem tocar as portas de conta e câmbio.
 */
public record NewTransaction(
        UUID userId,
        TransactionType type,
        BigDecimal amount,
        String description,
        TransactionCategory category,
        UUID destinationUserId
) {
    private static final int AMOUNT_SCALE = 2;

    public NewTransaction {
        if (userId == null) {
            throw new InvalidTransactionException("userId é obrigatório");
        }
        if (type == null) {
            throw new InvalidTransactionException("type é obrigatório");
        }
        amount = normalizeAmount(amount);
        description = requireDescription(description);
        category = resolveCategory(type, category);
        validateDestination(type, userId, destinationUserId);
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransactionException("amount é obrigatório");
        }
        if (amount.signum() <= 0) {
            throw new InvalidTransactionException("amount deve ser maior que zero");
        }
        if (amount.scale() > AMOUNT_SCALE) {
            throw new InvalidTransactionException("amount deve ter no máximo 2 casas decimais");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("description é obrigatória");
        }
        return description.strip();
    }

    private static TransactionCategory resolveCategory(TransactionType type, TransactionCategory category) {
        if (type == TransactionType.PURCHASE && category == null) {
            throw new InvalidTransactionException("category é obrigatória em transação de compra");
        }
        return category != null ? category : TransactionCategory.OTHER;
    }

    private static void validateDestination(TransactionType type, UUID userId, UUID destinationUserId) {
        if (type == TransactionType.TRANSFER) {
            if (destinationUserId == null) {
                throw new InvalidTransactionException("destinationUserId é obrigatório em transferência");
            }
            if (destinationUserId.equals(userId)) {
                throw new InvalidTransactionException("destinationUserId deve ser diferente do usuário dono");
            }
        } else if (destinationUserId != null) {
            throw new InvalidTransactionException("destinationUserId só é permitido em transferência");
        }
    }
}
