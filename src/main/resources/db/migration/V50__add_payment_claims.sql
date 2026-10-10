-- What a client says they paid, reported from a shared document, until
-- someone in the business confirms it as a payment or dismisses it. A report
-- never changes what is owed by itself.
CREATE TABLE payment_claims (
    pkid BIGSERIAL PRIMARY KEY,
    id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    business_pkid BIGINT NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    document_pkid BIGINT NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    paid_on DATE NOT NULL,
    method VARCHAR(20) NOT NULL,
    reference VARCHAR(60),
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    payment_pkid BIGINT REFERENCES payments (pkid) ON DELETE SET NULL,
    decided_by_pkid BIGINT REFERENCES users (pkid) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    dismiss_reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_claims_amount CHECK (amount > 0),
    CONSTRAINT chk_claims_method CHECK (method IN ('UPI', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT chk_claims_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT chk_claims_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

CREATE INDEX idx_claims_pending ON payment_claims (document_pkid) WHERE status = 'PENDING';
CREATE INDEX idx_claims_business ON payment_claims (business_pkid, status, created_at DESC);
