package br.com.cadeopila.transactionservice.core.domain;

/**
 * Lista fechada de categorias. O rótulo em pt-BR fica no {@code @Schema} do DTO web,
 * não no domínio. Usada na análise de despesas (agrupamento por categoria).
 */
public enum TransactionCategory {

    FOOD,
    TRANSPORT,
    HOUSING,
    HEALTH,
    EDUCATION,
    LEISURE,
    SHOPPING,
    BILLS,
    SALARY,
    OTHER
}
