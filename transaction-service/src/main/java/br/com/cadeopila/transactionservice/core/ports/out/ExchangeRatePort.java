package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.List;

/**
 * Cotações de câmbio das moedas configuradas para uma data. Tolerante a falha: a indisponibilidade
 * do câmbio resulta em lista vazia (ou sem a moeda afetada) e nunca bloqueia a criação da transação.
 * Quais moedas são cotadas é responsabilidade do adapter (configuração de infraestrutura).
 */
public interface ExchangeRatePort {

    List<ExchangeRate> quoteAll(LocalDate date);
}
