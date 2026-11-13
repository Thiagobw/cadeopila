package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;

import java.util.UUID;

public interface UpdateTransactionMetadataUseCase {

    Transaction update(Command command);

    /**
     * Edição parcial de metadados: pelo menos um entre {@code description} e {@code category} deve vir.
     * {@code userId} é o dono autenticado — usado para checar a propriedade da transação.
     */
    record Command(UUID id, UUID userId, String description, TransactionCategory category) {
        public Command {
            if (id == null) {
                throw new InvalidTransactionException("id é obrigatório");
            }
            if (userId == null) {
                throw new InvalidTransactionException("userId é obrigatório");
            }
            if ((description == null || description.isBlank()) && category == null) {
                throw new InvalidTransactionException(
                        "informe ao menos um campo para atualizar (description ou category)");
            }
        }
    }
}
