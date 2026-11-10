package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.UpdateTransactionMetadataUseCase.Command;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTransactionMetadataUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-06T18:00:00Z");

    @Mock
    private TransactionRepositoryPort transactionRepository;

    private UpdateTransactionMetadataUseCaseImpl useCase() {
        return new UpdateTransactionMetadataUseCaseImpl(transactionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Transaction purchase(UUID id) {
        return Transaction.rehydrate(id, UUID.randomUUID(), null, TransactionType.PURCHASE, new BigDecimal("150.00"),
                "Mercado", TransactionCategory.FOOD, TransactionStatus.APPROVED, null,
                NOW, List.of(), NOW, NOW, null, 0L);
    }

    @Test
    @DisplayName("Deve atualizar os metadados da transação ativa")
    void deveAtualizarMetadados() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.of(purchase(id)));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().update(new Command(id, "Mercado corrigido", TransactionCategory.SHOPPING));

        assertThat(result.getDescription()).isEqualTo("Mercado corrigido");
        assertThat(result.getCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Deve lançar 404 quando a transação não existe ou está excluída")
    void deveLancar404() {
        var id = UUID.randomUUID();
        when(transactionRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().update(new Command(id, "x", null)))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("Command deve exigir ao menos um campo (description ou category)")
    void commandDeveExigirAoMenosUmCampo() {
        var id = UUID.randomUUID();

        assertThatThrownBy(() -> new Command(id, null, null))
                .isInstanceOf(InvalidTransactionException.class);
    }
}
