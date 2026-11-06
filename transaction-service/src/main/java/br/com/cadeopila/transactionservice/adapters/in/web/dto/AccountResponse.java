package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Dados da conta na resposta do POST")
public record AccountResponse(

        @Schema(description = "Saldo após a operação: novo saldo se aprovada, saldo consultado se rejeitada, "
                + "null se a API de contas não respondeu", example = "14850.00")
        BigDecimal balanceAfter
) {}
