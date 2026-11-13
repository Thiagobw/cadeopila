package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;

public interface ListTransactionsUseCase {

    PageResult<Transaction> list(TransactionQuery query);
}
