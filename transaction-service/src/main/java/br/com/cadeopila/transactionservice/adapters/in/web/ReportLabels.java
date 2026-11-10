package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;

/**
 * Rótulos pt-BR dos enums do domínio, usados nos relatórios exportados (CSV/Excel). Vive na camada de
 * adapters porque "como o enum aparece para o usuário" é detalhe de apresentação — o domínio permanece
 * com nomes neutros (convenção do projeto: rótulo pt-BR no adapter, nunca no core). Compartilhado pelos
 * dois <i>writers</i> para não duplicar a tradução.
 */
final class ReportLabels {

    private ReportLabels() {
    }

    static String type(TransactionType type) {
        return switch (type) {
            case DEPOSIT -> "Depósito";
            case WITHDRAWAL -> "Saque";
            case TRANSFER -> "Transferência";
            case PURCHASE -> "Compra";
        };
    }

    static String status(TransactionStatus status) {
        return switch (status) {
            case APPROVED -> "Aprovada";
            case REJECTED -> "Rejeitada";
        };
    }

    static String categoryLevel1(CategoryLevel1 level1) {
        if (level1 == null) {
            return "";
        }
        return switch (level1) {
            case ESSENCIAL -> "Essencial";
            case ESTILO_DE_VIDA -> "Estilo de vida";
            case SAUDE_FINANCEIRA -> "Saúde financeira";
            case DIVIDAS_IMPREVISTOS -> "Dívidas e imprevistos";
        };
    }

    /** {@code null} (transação aprovada) vira string vazia. */
    static String rejectionReason(RejectionReason reason) {
        if (reason == null) {
            return "";
        }
        return switch (reason) {
            case TRANSACTION_LIMIT_EXCEEDED -> "Acima do limite por transação";
            case INSUFFICIENT_BALANCE -> "Saldo insuficiente";
            case DAILY_LIMIT_EXCEEDED -> "Acima do limite diário";
            case ACCOUNT_NOT_FOUND -> "Conta não encontrada";
            case ACCOUNT_SERVICE_UNAVAILABLE -> "Serviço de contas indisponível";
        };
    }
}
