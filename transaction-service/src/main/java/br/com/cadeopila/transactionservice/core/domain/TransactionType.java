package br.com.cadeopila.transactionservice.core.domain;

public enum TransactionType {

    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    PURCHASE;

    /** Saída de dinheiro da conta do dono (tudo que não é depósito). */
    public boolean isOutflow() {
        return this != DEPOSIT;
    }
}
