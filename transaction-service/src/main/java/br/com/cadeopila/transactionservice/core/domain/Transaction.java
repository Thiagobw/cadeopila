package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agregado de transação. Modelo rico, imutável e sem setters: o estado só nasce por
 * fábricas de intenção ({@link #approved}/{@link #rejected}) ou é reconstruído da
 * persistência por {@link #rehydrate}. Edição de metadados e exclusão lógica entram na
 * história de PATCH/DELETE (DBTB-58).
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
