CREATE TABLE transactions (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL,
    destination_user_id UUID,
    type                VARCHAR(20)   NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    description         VARCHAR(255)  NOT NULL,
    category            VARCHAR(20)   NOT NULL,
    status              VARCHAR(10)   NOT NULL,
    rejection_reason    VARCHAR(40),
    occurred_at         TIMESTAMPTZ   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT ck_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_tx_type     CHECK (type   IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PURCHASE')),
    CONSTRAINT ck_tx_status   CHECK (status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_tx_category CHECK (category IN
        ('FOOD', 'TRANSPORT', 'HOUSING', 'HEALTH', 'EDUCATION',
         'LEISURE', 'SHOPPING', 'BILLS', 'SALARY', 'OTHER')),
    CONSTRAINT ck_tx_status_reason CHECK (
        (status = 'APPROVED' AND rejection_reason IS NULL) OR
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)),
    CONSTRAINT ck_tx_transfer_dest CHECK (
        (type  = 'TRANSFER' AND destination_user_id IS NOT NULL AND destination_user_id <> user_id) OR
        (type <> 'TRANSFER' AND destination_user_id IS NULL))
);

-- Índice da listagem por usuário/período, restrito às transações ativas.
CREATE INDEX ix_transactions_user_occurred
    ON transactions (user_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

-- Cotações de câmbio gravadas como snapshot (parte do agregado Transaction).
CREATE TABLE transaction_exchange_quotes (
    transaction_id   UUID          NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    rate             NUMERIC(19,6) NOT NULL,
    converted_amount NUMERIC(19,2) NOT NULL,
    quoted_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_tx_exchange_quotes PRIMARY KEY (transaction_id, currency),
    CONSTRAINT fk_teq_transaction FOREIGN KEY (transaction_id)
        REFERENCES transactions (id) ON DELETE CASCADE
);
