-- Money a business spends, for its profit and loss. The amount leaves out
-- the tax paid on it: tax the business can claim back is not a cost, and the
-- rest of it is. The date is the day it was paid.
CREATE TABLE expenses (
    pkid BIGSERIAL PRIMARY KEY,
    id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    business_pkid BIGINT NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    expense_date DATE NOT NULL,
    vendor VARCHAR(255),
    category VARCHAR(60) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    tax_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    tax_reclaimable BOOLEAN NOT NULL DEFAULT FALSE,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_expenses_amount CHECK (amount > 0 AND tax_amount >= 0),
    CONSTRAINT chk_expenses_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_expenses_business_date ON expenses (business_pkid, expense_date);
