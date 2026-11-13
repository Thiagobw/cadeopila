package br.com.cadeopila.transactionservice.adapters.out.persistence.mapper;

import br.com.cadeopila.transactionservice.adapters.out.persistence.ExchangeQuoteEmbeddable;
import br.com.cadeopila.transactionservice.adapters.out.persistence.TransactionJpaEntity;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionPersistenceMapper {

    public TransactionJpaEntity toEntity(Transaction transaction) {
        var entity = new TransactionJpaEntity();
        if (transaction.getId() != null) {
            entity.setId(transaction.getId());
        }
        entity.setUserId(transaction.getUserId());
        entity.setDestinationUserId(transaction.getDestinationUserId());
        entity.setType(transaction.getType());
        entity.setAmount(transaction.getAmount());
        entity.setDescription(transaction.getDescription());
        entity.setCategory(transaction.getCategory());
        entity.setCategoryLevel1(transaction.getCategoryLevel1());
        entity.setCategoryLevel2(transaction.getCategoryLevel2());
        entity.setFixed(transaction.isFixed());
        entity.setRecurring(transaction.isRecurring());
        entity.setStatus(transaction.getStatus());
        entity.setRejectionReason(transaction.getRejectionReason());
        entity.setOccurredAt(transaction.getOccurredAt());
        entity.setCreatedAt(transaction.getCreatedAt());
        entity.setUpdatedAt(transaction.getUpdatedAt());
        entity.setDeletedAt(transaction.getDeletedAt());
        entity.setVersion(transaction.getVersion());
        entity.setExchangeQuotes(toEmbeddables(transaction.getExchangeQuotes()));
        return entity;
    }

    public Transaction toDomain(TransactionJpaEntity entity) {
        var level1 = entity.getCategoryLevel1() != null
                ? entity.getCategoryLevel1()
                : CategoryLevel1.defaultFor(entity.getCategory());
        return Transaction.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getDestinationUserId(),
                entity.getType(),
                entity.getAmount(),
                entity.getDescription(),
                entity.getCategory(),
                level1,
                entity.getCategoryLevel2(),
                entity.isFixed(),
                entity.isRecurring(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getOccurredAt(),
                toQuotes(entity.getExchangeQuotes()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getVersion());
    }

    private List<ExchangeQuoteEmbeddable> toEmbeddables(List<ExchangeQuote> quotes) {
        var result = new ArrayList<ExchangeQuoteEmbeddable>(quotes.size());
        for (var quote : quotes) {
            var embeddable = new ExchangeQuoteEmbeddable();
            embeddable.setCurrency(quote.currency());
            embeddable.setRate(quote.rate());
            embeddable.setConvertedAmount(quote.convertedAmount());
            embeddable.setSource(quote.source());
            embeddable.setQuotedAt(quote.quotedAt());
            result.add(embeddable);
        }
        return result;
    }

    private List<ExchangeQuote> toQuotes(List<ExchangeQuoteEmbeddable> embeddables) {
        return embeddables.stream()
                .map(e -> new ExchangeQuote(e.getCurrency(), e.getRate(), e.getConvertedAmount(), e.getSource(), e.getQuotedAt()))
                .toList();
    }
}
