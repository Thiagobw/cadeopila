package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotDeletableException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteTransactionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-06T18:00:00Z");
    private static final UUID OWNER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final UUID OTHER = UUID.fromString("9f1c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8");

    @Mock
    private TransactionRepositoryPort transactionRepository;

    private DeleteTransactionUseCaseImpl useCase() {
        return new DeleteTransactionUseCaseImpl(transactionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Transaction withStatus(UUID id, TransactionStatus status) {
        var reason = status == TransactionStatus.REJECTED ? RejectionReason.INSUFFICIENT_BALANCE : null;
        return Transaction.rehydrate(id, OWNER, null, TransactionType.WITHDRAWAL, new BigDecimal("40.00"),
                "Saque", TransactionCategory.OTHER, status, reason, NOW, List.of(), NOW, NOW, null, 0L);
    }

    @Test
    @DisplayName("Deve excluir logicamente uma transação REJECTED do próprio usuário")
    void deveExcluirRejeitada() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(withStatus(id, TransactionStatus.REJECTED)));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase().delete(id, OWNER);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação não existe ou está excluída")
    void deveLancar404() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().delete(id, OWNER)).isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação pertence a outro usuário e não gravar")
    void deveLancar404QuandoNaoDono() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(withStatus(id, TransactionStatus.REJECTED)));

        assertThatThrownBy(() -> useCase().delete(id, OTHER)).isInstanceOf(TransactionNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar (422) a exclusão de transação APPROVED e não gravar")
    void deveRecusarApproved() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(withStatus(id, TransactionStatus.APPROVED)));

        assertThatThrownBy(() -> useCase().delete(id, OWNER)).isInstanceOf(TransactionNotDeletableException.class);
        verify(transactionRepository, never()).save(any());
    }
}
