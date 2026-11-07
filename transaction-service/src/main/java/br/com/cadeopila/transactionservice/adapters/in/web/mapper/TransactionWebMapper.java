package br.com.cadeopila.transactionservice.adapters.in.web.mapper;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.AccountResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionRequest;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.CreateTransactionResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExchangeQuoteResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExchangeResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.TransactionResponse;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
import org.springframework.stereotype.Component;

@Component
public class TransactionWebMapper {

    private static final String EXCHANGE_UNAVAILABLE_MESSAGE = "Conversão indisponível no momento";

    public CreateTransactionUseCase.Command toCommand(CreateTransactionRequest request) {
        return new CreateTransactionUseCase.Command(
                request.userId(),
                request.type(),
                request.amount(),
                request.description(),
                request.category(),
                request.destinationUserId());
    }

    public CreateTransactionResponse toCreateResponse(CreateTransactionUseCase.Result result) {
        var transaction = result.transaction();
        return new CreateTransactionResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getRejectionReason(),
                toExchange(transaction),
                new AccountResponse(result.balanceAfter()));
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getDestinationUserId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCategory(),
                transaction.getStatus(),
                transaction.getRejectionReason(),
                transaction.getOccurredAt(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                toExchange(transaction));
    }

    private ExchangeResponse toExchange(Transaction transaction) {
        var quotes = transaction.getExchangeQuotes().stream()
                .map(q -> new ExchangeQuoteResponse(q.currency(), q.rate(), q.convertedAmount(), q.quotedAt()))
                .toList();
        boolean available = !quotes.isEmpty();
        String message = available ? null : EXCHANGE_UNAVAILABLE_MESSAGE;
        return new ExchangeResponse(available, transaction.getAmount(), message, quotes);
    }
}
