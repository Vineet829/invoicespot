-- Money paid back to a client when a credit note leaves an invoice or receipt
-- overpaid. Refunds live apart from payments, so nothing that adds up
-- payments can count one as money received. Each document keeps the total
-- it refunded.
CREATE TABLE refunds (
    pkid           BIGSERIAL PRIMARY KEY,
    id             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    document_pkid  BIGINT NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    date_refunded  DATE NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    reference      VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_refunds_amount CHECK (amount > 0),
    CONSTRAINT chk_refunds_method CHECK (payment_method IN ('Cash', 'Mobile Money', 'PayPal', 'Credit Card',
        'Bank Transfer', 'UPI', 'Cheque', 'Others'))
);
CREATE INDEX idx_refunds_document ON refunds (document_pkid);

ALTER TABLE documents ADD COLUMN total_refunded NUMERIC(19, 2) NOT NULL DEFAULT 0;
