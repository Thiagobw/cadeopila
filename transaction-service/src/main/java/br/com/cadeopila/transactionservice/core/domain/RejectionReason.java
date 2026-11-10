package br.com.cadeopila.transactionservice.core.domain;

public enum RejectionReason {

    TRANSACTION_LIMIT_EXCEEDED,
    INSUFFICIENT_BALANCE,
    DAILY_LIMIT_EXCEEDED,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_SERVICE_UNAVAILABLE
}
