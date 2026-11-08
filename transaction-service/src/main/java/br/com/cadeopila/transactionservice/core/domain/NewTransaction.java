package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Dados de uma nova transação já validados quanto às invariantes de domínio que dependem
 * do tipo (regras de formato de campo ficam na Bean Validation do DTO). É construído antes
 * de qualquer chamada externa: uma entrada inválida falha sem tocar as portas de conta e câmbio.
 *
 * <p>Carrega também a classificação inteligente da despesa — {@link CategoryLevel1 nível 1}
 * (grau de necessidade), subcategoria livre (nível 2) e as flags de previsibilidade
 * ({@code fixed}/{@code recurring}) — opcionais na criação. Quando o nível 1 não é informado,
 * é derivado da categoria ({@link CategoryLevel1#defaultFor}), de modo que toda transação tenha
 * um grau de necessidade para a análise.</p>
 */
public record NewTransaction(
        UUID userId,
        TransactionType type,
        BigDecimal amount,
        String description,
        TransactionCategory category,
        UUID destinationUserId,
        CategoryLevel1 categoryLevel1,
        String categoryLevel2,
        boolean fixed,
        boolean recurring
) {
    private static final int AMOUNT_SCALE = 2;

    /** Subcategoria "catch-all" que dispara a regra de fricção (o "buraco negro"). */
    static final String OUTROS_SUBCATEGORY = "OUTROS";
    private static final int OUTROS_MIN_DESCRIPTION_LENGTH = 5;

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
        categoryLevel2 = normalizeSubcategory(categoryLevel2);
        requireRichDescriptionWhenOutros(categoryLevel2, description);
        categoryLevel1 = resolveLevel1(category, categoryLevel1);
        validateDestination(type, userId, destinationUserId);
    }

    /**
     * Construtor de compatibilidade: a criação "clássica" (sem a hierarquia inteligente) continua
     * válida — nível 1 é derivado da categoria, nível 2 fica vazio e as flags assumem {@code false}.
     */
    public NewTransaction(UUID userId, TransactionType type, BigDecimal amount, String description,
                          TransactionCategory category, UUID destinationUserId) {
        this(userId, type, amount, description, category, destinationUserId, null, null, false, false);
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

    private static String normalizeSubcategory(String categoryLevel2) {
        if (categoryLevel2 == null || categoryLevel2.isBlank()) {
            return null;
        }
        return categoryLevel2.strip();
    }

    /**
     * Regra de fricção (o "buraco negro"): usar a subcategoria genérica "OUTROS" exige uma descrição
     * com no mínimo 5 caracteres, para que o gasto não desapareça num balde sem explicação.
     */
    private static void requireRichDescriptionWhenOutros(String categoryLevel2, String description) {
        if (categoryLevel2 != null && OUTROS_SUBCATEGORY.equalsIgnoreCase(categoryLevel2)
                && description.length() < OUTROS_MIN_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionException(
                    "description é obrigatória e deve ter no mínimo 5 caracteres quando a subcategoria é OUTROS");
        }
    }

    private static CategoryLevel1 resolveLevel1(TransactionCategory category, CategoryLevel1 categoryLevel1) {
        return categoryLevel1 != null ? categoryLevel1 : CategoryLevel1.defaultFor(category);
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
