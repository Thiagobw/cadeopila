package br.com.cadeopila.transactionservice.core.domain;

/**
 * Nível 1 da hierarquia de categorias — o <b>grau de necessidade</b> da despesa. Lista fechada
 * (os rótulos em pt-BR ficam no {@code @Schema} do DTO web, não no domínio). É a base do bloco
 * "Raio-X" da análise de despesas.
 */
public enum CategoryLevel1 {

    ESSENCIAL,
    ESTILO_DE_VIDA,
    SAUDE_FINANCEIRA,
    DIVIDAS_IMPREVISTOS;

    /**
     * Blocos cujo gasto é discricionário/evitável — os únicos considerados no "Onde Cortar Hoje".
     */
    public boolean isDiscretionary() {
        return this == ESTILO_DE_VIDA || this == DIVIDAS_IMPREVISTOS;
    }

    /**
     * Classificação padrão derivada da categoria (nível 2 "clássico") quando o nível 1 não é
     * informado na criação. Garante que toda despesa tenha um grau de necessidade para a análise,
     * sem quebrar o contrato de criação existente (onde o campo é opcional).
     */
    public static CategoryLevel1 defaultFor(TransactionCategory category) {
        return switch (category) {
            case FOOD, TRANSPORT, HOUSING, HEALTH, EDUCATION, BILLS -> ESSENCIAL;
            case LEISURE, SHOPPING -> ESTILO_DE_VIDA;
            case SALARY -> SAUDE_FINANCEIRA;
            case OTHER -> DIVIDAS_IMPREVISTOS;
        };
    }
}
