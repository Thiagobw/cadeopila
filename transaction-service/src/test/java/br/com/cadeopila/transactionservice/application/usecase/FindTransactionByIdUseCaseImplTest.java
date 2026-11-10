package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindTransactionByIdUseCaseImplTest {

    @Mock
    private TransactionRepositoryPort transactionRepository;

    @InjectMocks
    private FindTransactionByIdUseCaseImpl useCase;

    @Test
    @DisplayName("Deve retornar a transação quando ativa")
    void deveRetornarQuandoAtiva() {
        var id = UUID.randomUUID();
        var now = Instant.parse("2026-11-06T18:00:00Z");
        var tx = Transaction.rehydrate(id, UUID.randomUUID(), null, TransactionType.DEPOSIT,
                new BigDecimal("100.00"), "x", TransactionCategory.OTHER, TransactionStatus.APPROVED, null,
                now, List.of(), now, now, null, 0L);
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(tx));

        assertThat(useCase.findById(id)).isSameAs(tx);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação não existe ou está excluída")
    void deveLancarQuandoNaoEncontrada() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findById(id))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
