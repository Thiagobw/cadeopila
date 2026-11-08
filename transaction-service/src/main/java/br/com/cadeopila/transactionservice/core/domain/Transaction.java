package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotDeletableException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agregado de transação. Modelo rico, imutável e sem setters: o estado só nasce por
 * fábricas de intenção ({@link #approved}/{@link #rejected}) ou é reconstruído da
 * persistência por {@link #rehydrate}. A correção é feita por edição de metadados
 * ({@link #updateMetadata}) e a "remoção" é exclusão lógica ({@link #markAsDeleted}),
 * nunca apagando o histórico — conforme pedido do PO.
 */
public final class Transaction {

    private final UUID id;
    private final UUID userId;
    private final UUID destinationUserId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final TransactionCategory category;
    private final TransactionStatus status;
    private final RejectionReason rejectionReason;
    private final Instant occurredAt;
    private final List<ExchangeQuote> exchangeQuotes;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;
    private final long version;

    private Transaction(UUID id, UUID userId, UUID destinationUserId, TransactionType type,
                        BigDecimal amount, String description, TransactionCategory category,
                        TransactionStatus status, RejectionReason rejectionReason, Instant occurredAt,
                        List<ExchangeQuote> exchangeQuotes, Instant createdAt, Instant updatedAt,
                        Instant deletedAt, long version) {
        this.id = id;
        this.userId = userId;
        this.destinationUserId = destinationUserId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.occurredAt = occurredAt;
        this.exchangeQuotes = exchangeQuotes == null ? List.of() : List.copyOf(exchangeQuotes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Transaction approved(NewTransaction data, Instant occurredAt,
                                       List<ExchangeQuote> quotes, Instant now) {
        return new Transaction(null, data.userId(), data.destinationUserId(), data.type(),
                data.amount(), data.description(), data.category(),
                TransactionStatus.APPROVED, null, occurredAt,
                quotes, now, now, null, 0L);
    }

    public static Transaction rejected(NewTransaction data, RejectionReason reason, Instant occurredAt,
                                       List<ExchangeQuote> quotes, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("rejectionReason é obrigatório em transação REJECTED");
        }
        return new Transaction(null, data.userId(), data.destinationUserId(), data.type(),
                data.amount(), data.description(), data.category(),
                TransactionStatus.REJECTED, reason, occurredAt,
                quotes, now, now, null, 0L);
    }

    /** Reconstrói o agregado a partir da persistência; não revalida as regras de criação. */
    public static Transaction rehydrate(UUID id, UUID userId, UUID destinationUserId, TransactionType type,
                                        BigDecimal amount, String description, TransactionCategory category,
                                        TransactionStatus status, RejectionReason rejectionReason,
                                        Instant occurredAt, List<ExchangeQuote> exchangeQuotes,
                                        Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        return new Transaction(id, userId, destinationUserId, type, amount, description, category,
                status, rejectionReason, occurredAt, exchangeQuotes, createdAt, updatedAt, deletedAt, version);
    }

    /**
     * Edita apenas os metadados (descrição e categoria). Campos nulos são mantidos. Valor, tipo, dono,
     * destino, status, motivo e datas de ocorrência/criação são imutáveis. Retorna uma nova instância.
     */
    public Transaction updateMetadata(String newDescription, TransactionCategory newCategory, Instant now) {
        var updatedDescription = this.description;
        if (newDescription != null) {
            if (newDescription.isBlank()) {
                throw new InvalidTransactionException("description não pode ser vazia");
            }
            updatedDescription = newDescription.strip();
        }
        var updatedCategory = newCategory != null ? newCategory : this.category;
        if (type == TransactionType.PURCHASE && updatedCategory == null) {
            throw new InvalidTransactionException("category é obrigatória em transação de compra");
        }
        return new Transaction(id, userId, destinationUserId, type, amount, updatedDescription, updatedCategory,
                status, rejectionReason, occurredAt, exchangeQuotes, createdAt, now, deletedAt, version);
    }

    /**
     * Exclusão lógica: só é permitida em transação REJECTED (preserva o histórico). Retorna uma nova
     * instância com {@code deletedAt} preenchido.
     */
    public Transaction markAsDeleted(Instant now) {
        if (status != TransactionStatus.REJECTED) {
            throw new TransactionNotDeletableException();
        }
        return new Transaction(id, userId, destinationUserId, type, amount, description, category,
                status, rejectionReason, occurredAt, exchangeQuotes, createdAt, now, now, version);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOutflow() {
        return type.isOutflow();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getDestinationUserId() { return destinationUserId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public TransactionCategory getCategory() { return category; }
    public TransactionStatus getStatus() { return status; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public Instant getOccurredAt() { return occurredAt; }
    public List<ExchangeQuote> getExchangeQuotes() { return exchangeQuotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
}
