package br.com.cadeopila.userservice.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @ParameterizedTest
    @DisplayName("Deve normalizar o email com trim e toLowerCase")
    @CsvSource({
            "'  Maria.Souza@Email.COM  ', maria.souza@email.com",
            "'USER@EXAMPLE.ORG', user@example.org",
            "'  spaced@domain.com', spaced@domain.com",
            "'MixedCase@Host.Co  ', mixedcase@host.co"
    })
    void deveNormalizarEmail(String entrada, String esperado) {
        // When
        var email = new Email(entrada);

        // Then
        assertThat(email.value()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Deve rejeitar email nulo")
    void deveRejeitarEmailNulo() {
        // When / Then
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @DisplayName("Deve rejeitar email em branco")
    @ValueSource(strings = {"", "   ", "\t"})
    void deveRejeitarEmailEmBranco(String entrada) {
        // When / Then
        assertThatThrownBy(() -> new Email(entrada))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("normalize estático deve devolver null para entrada nula")
    void normalizeDeveDevolverNullParaEntradaNula() {
        assertThat(Email.normalize(null)).isNull();
    }

    @Test
    @DisplayName("normalize estático deve aplicar trim e toLowerCase")
    void normalizeDeveAplicarTrimELowerCase() {
        assertThat(Email.normalize("  ABC@Def.COM ")).isEqualTo("abc@def.com");
    }
}
