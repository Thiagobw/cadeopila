package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewTransactionTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID DESTINATION = UUID.randomUUID();
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Test
    @DisplayName("Deve criar compra com categoria informada")
    void deveCriarCompraComCategoria() {
        var data = new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Mercado",
                TransactionCategory.FOOD, null);

        assertThat(data.category()).isEqualTo(TransactionCategory.FOOD);
    }

    @Test
    @DisplayName("Deve assumir categoria OTHER quando ausente em tipo que não é compra")
    void deveAssumirCategoriaOtherQuandoAusente() {
        var data = new NewTransaction(OWNER, TransactionType.DEPOSIT, TEN, "Salário", null, null);

        assertThat(data.category()).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    @DisplayName("Deve rejeitar compra sem categoria")
    void deveRejeitarCompraSemCategoria() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Mercado", null, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("category");
    }

    @Test
    @DisplayName("Deve exigir usuário de destino em transferência")
    void deveExigirDestinoEmTransferencia() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.TRANSFER, TEN, "PIX", null, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("destinationUserId");
    }

    @Test
    @DisplayName("Deve rejeitar transferência cujo destino é o próprio dono")
    void deveRejeitarDestinoIgualAoDono() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.TRANSFER, TEN, "PIX", null, OWNER))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("diferente");
    }

    @Test
    @DisplayName("Deve rejeitar destino informado em tipo que não é transferência")
    void deveRejeitarDestinoEmTipoNaoTransferencia() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.WITHDRAWAL, TEN, "Saque", null, DESTINATION))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("só é permitido em transferência");
    }

    @Test
    @DisplayName("Deve aceitar transferência com destino diferente do dono")
    void deveAceitarTransferenciaValida() {
        assertThatCode(() -> new NewTransaction(OWNER, TransactionType.TRANSFER, TEN, "PIX", null, DESTINATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Deve normalizar o valor para duas casas decimais")
    void deveNormalizarAmountParaDuasCasas() {
        var data = new NewTransaction(OWNER, TransactionType.DEPOSIT, new BigDecimal("150"), "Depósito", null, null);

        assertThat(data.amount()).isEqualByComparingTo("150.00");
        assertThat(data.amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve rejeitar valor com mais de duas casas decimais")
    void deveRejeitarAmountComMaisDeDuasCasas() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.DEPOSIT, new BigDecimal("10.555"), "x", null, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("casas decimais");
    }

    @ParameterizedTest
    @DisplayName("Deve rejeitar valor zero ou negativo")
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void deveRejeitarAmountNaoPositivo(String amount) {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.DEPOSIT, new BigDecimal(amount), "x", null, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    @DisplayName("Deve rejeitar descrição em branco")
    void deveRejeitarDescricaoEmBranco() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.DEPOSIT, TEN, "   ", null, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("description");
    }

    @Test
    @DisplayName("Deve remover espaços ao redor da descrição")
    void deveRemoverEspacosDaDescricao() {
        var data = new NewTransaction(OWNER, TransactionType.DEPOSIT, TEN, "  Salário  ", null, null);

        assertThat(data.description()).isEqualTo("Salário");
    }

    // ---------- Pedido 5: classificação inteligente ----------

    @Test
    @DisplayName("Regra de fricção: subcategoria OUTROS exige descrição com no mínimo 5 caracteres")
    void deveExigirDescricaoMinimaQuandoOutros() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Café",
                TransactionCategory.OTHER, null, null, "OUTROS", false, false))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("OUTROS");
    }

    @Test
    @DisplayName("Regra de fricção é case-insensitive para OUTROS")
    void deveAplicarFriccaoCaseInsensitive() {
        assertThatThrownBy(() -> new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "abc",
                TransactionCategory.OTHER, null, null, "outros", false, false))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("5 caracteres");
    }

    @Test
    @DisplayName("OUTROS com descrição suficiente (>= 5) é aceito")
    void deveAceitarOutrosComDescricaoSuficiente() {
        assertThatCode(() -> new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Padaria da esquina",
                TransactionCategory.OTHER, null, null, "OUTROS", false, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Nível 1 ausente é derivado da categoria (FOOD → ESSENCIAL)")
    void deveDerivarNivel1DaCategoria() {
        var data = new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Mercado",
                TransactionCategory.FOOD, null);

        assertThat(data.categoryLevel1()).isEqualTo(CategoryLevel1.ESSENCIAL);
    }

    @Test
    @DisplayName("Nível 1 informado é preservado")
    void devePreservarNivel1Informado() {
        var data = new NewTransaction(OWNER, TransactionType.PURCHASE, TEN, "Cinema",
                TransactionCategory.LEISURE, null, CategoryLevel1.ESTILO_DE_VIDA, "Streaming", true, true);

        assertThat(data.categoryLevel1()).isEqualTo(CategoryLevel1.ESTILO_DE_VIDA);
        assertThat(data.categoryLevel2()).isEqualTo("Streaming");
        assertThat(data.fixed()).isTrue();
        assertThat(data.recurring()).isTrue();
    }

    @Test
    @DisplayName("Subcategoria em branco é normalizada para nulo")
    void deveNormalizarSubcategoriaEmBranco() {
        var data = new NewTransaction(OWNER, TransactionType.DEPOSIT, TEN, "Salário",
                null, null, null, "   ", false, false);

        assertThat(data.categoryLevel2()).isNull();
    }
}
