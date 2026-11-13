package br.com.cadeopila.transactionservice.adapters.out.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta da BrasilAPI em {@code /cambio/v1/cotacao/{moeda}/{data}}. Mapeia apenas o que é usado;
 * campos desconhecidos (paridade, cotação de compra) são ignorados.
 */
public record BrasilApiCambioResponse(List<Cotacao> cotacoes) {

    public record Cotacao(
            @JsonProperty("cotacao_venda") BigDecimal cotacaoVenda,
            @JsonProperty("data_hora_cotacao") String dataHoraCotacao,
            @JsonProperty("tipo_boletim") String tipoBoletim
    ) {}
}
