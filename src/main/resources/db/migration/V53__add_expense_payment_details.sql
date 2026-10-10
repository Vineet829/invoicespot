-- How an expense was paid and recorded: the payment method, a reference such
-- as a bill or transaction number, notes, and the member who recorded it.
-- Like a document, an expense in another currency can keep its rate to the
-- reporting currency.
ALTER TABLE expenses
    ADD COLUMN recorded_by_pkid BIGINT REFERENCES users (pkid) ON DELETE SET NULL,
    ADD COLUMN payment_method VARCHAR(30),
    ADD COLUMN reference VARCHAR(100),
    ADD COLUMN notes TEXT,
    ADD COLUMN exchange_rate NUMERIC(20, 10),
    ADD COLUMN exchange_base VARCHAR(3),
    ADD CONSTRAINT chk_expenses_exchange CHECK ((exchange_rate IS NULL AND exchange_base IS NULL)
        OR (exchange_rate > 0 AND exchange_base IS NOT NULL AND exchange_base <> currency)),
    ADD CONSTRAINT chk_expenses_method CHECK (payment_method IN ('Cash', 'Mobile Money', 'PayPal', 'Credit Card',
        'Bank Transfer', 'UPI', 'Cheque', 'Others'));
