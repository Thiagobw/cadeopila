package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionQueryTest {

    private static final UUID USER = UUID.randomUUID();
    private static final PageQuery PAGE = new PageQuery(0, 20);

    @Test
    @DisplayName("Deve exigir userId")
    void deveExigirUserId() {
        assertThatThrownBy(() -> new TransactionQuery(null, null, null, null, null, PAGE))
                .isInstanceOf(InvalidTransactionFilterException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("Deve rejeitar período com início depois do fim")
    void deveRejeitarPeriodoInvertido() {
        var from = LocalDate.of(2026, 11, 10);
        var to = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> new TransactionQuery(USER, null, null, from, to, PAGE))
                .isInstanceOf(InvalidTransactionFilterException.class)
                .hasMessageContaining("from");
    }

    @Test
    @DisplayName("Deve aceitar período com início igual ao fim")
    void deveAceitarPeriodoIgual() {
        var day = LocalDate.of(2026, 11, 6);

        assertThatCode(() -> new TransactionQuery(USER, null, null, day, day, PAGE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Deve aceitar query apenas com userId (demais filtros opcionais)")
    void deveAceitarApenasUserId() {
        assertThatCode(() -> new TransactionQuery(USER, null, null, null, null, PAGE))
                .doesNotThrowAnyException();
    }
}
