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

    private static final UUID OWNER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final UUID OTHER = UUID.fromString("9f1c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8");

    @Mock
    private TransactionRepositoryPort transactionRepository;

    @InjectMocks
    private FindTransactionByIdUseCaseImpl useCase;

    private Transaction ownedBy(UUID id, UUID owner) {
        var now = Instant.parse("2026-11-06T18:00:00Z");
        return Transaction.rehydrate(id, owner, null, TransactionType.DEPOSIT,
                new BigDecimal("100.00"), "x", TransactionCategory.OTHER, TransactionStatus.APPROVED, null,
                now, List.of(), now, now, null, 0L);
    }

    @Test
    @DisplayName("Deve retornar a transação quando ativa e pertence ao usuário")
    void deveRetornarQuandoAtivaEDono() {
        var id = UUID.randomUUID();
        var tx = ownedBy(id, OWNER);
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(tx));

        assertThat(useCase.findById(id, OWNER)).isSameAs(tx);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação não existe ou está excluída")
    void deveLancarQuandoNaoEncontrada() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findById(id, OWNER))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação pertence a outro usuário (não vaza existência)")
    void deveLancar404QuandoNaoDono() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(ownedBy(id, OWNER)));

        assertThatThrownBy(() -> useCase.findById(id, OTHER))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
