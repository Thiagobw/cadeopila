package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.PageQuery;
import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTransactionsUseCaseImplTest {

    @Mock
    private TransactionRepositoryPort transactionRepository;

    @InjectMocks
    private ListTransactionsUseCaseImpl useCase;

    @Test
    @DisplayName("Deve delegar a busca paginada ao repositório")
    void deveDelegarAoRepositorio() {
        var query = new TransactionQuery(UUID.randomUUID(), null, null, null, null, new PageQuery(0, 20));
        PageResult<Transaction> expected = new PageResult<>(List.of(), 0, 0, 0, 20);
        when(transactionRepository.search(query)).thenReturn(expected);

        assertThat(useCase.list(query)).isSameAs(expected);
    }
}
