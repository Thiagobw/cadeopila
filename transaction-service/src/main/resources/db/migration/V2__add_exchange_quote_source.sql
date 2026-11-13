-- Origem da taxa de câmbio gravada no snapshot. Registros existentes recebem 'BrasilAPI'.
ALTER TABLE transaction_exchange_quotes
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'BrasilAPI';
