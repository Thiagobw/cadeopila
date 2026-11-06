package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Cotação de câmbio de uma moeda numa data. Tolerante a falha: a indisponibilidade do câmbio
 * resulta em {@code Optional.empty()} e nunca bloqueia a criação da transação.
 */
public interface ExchangeRatePort {

    Optional<ExchangeRate> quote(String currency, LocalDate date);
}
