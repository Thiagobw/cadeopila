package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
import br.com.cadeopila.transactionservice.core.ports.in.ListTransactionsUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTransactionsUseCaseImpl implements ListTransactionsUseCase {

    private final TransactionRepositoryPort transactionRepository;

    public ListTransactionsUseCaseImpl(TransactionRepositoryPort transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Transaction> list(TransactionQuery query) {
        return transactionRepository.search(query);
    }
}
