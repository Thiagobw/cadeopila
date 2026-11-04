package br.com.cadeopila.userservice.core.domain;

import br.com.cadeopila.userservice.core.exception.InvalidPaginationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    @DisplayName("Deve aceitar parâmetros de paginação válidos")
    void deveAceitarParametrosValidos() {
        // When
        var query = new PageQuery(1, 50);

        // Then
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("Deve aceitar o tamanho máximo permitido")
    void deveAceitarTamanhoMaximo() {
        // When / Then
        assertThatCode(() -> new PageQuery(0, PageQuery.MAX_SIZE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("Deve rejeitar parâmetros de paginação inválidos")
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101",
            "-5, 50"
    })
    void deveRejeitarParametrosInvalidos(int page, int size) {
        // When / Then
        assertThatThrownBy(() -> new PageQuery(page, size))
                .isInstanceOf(InvalidPaginationException.class);
    }
}
