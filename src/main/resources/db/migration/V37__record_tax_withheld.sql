-- A client can withhold tax from a payment (TDS) and pay it to the tax office
-- for you; it settles the invoice like money received, but isn't cash. Each
-- payment keeps what was withheld, and each document the total withheld. A
-- payment may now be only tax withheld. Old payments of 0 stay as they are,
-- so the check applies to new rows only.
ALTER TABLE payments ADD COLUMN tds_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE payments DROP CONSTRAINT chk_payments_amount;
ALTER TABLE payments ADD CONSTRAINT chk_payments_amount
    CHECK (amount_paid >= 0 AND tds_amount >= 0 AND amount_paid + tds_amount > 0) NOT VALID;

ALTER TABLE documents ADD COLUMN total_tds NUMERIC(19, 2) NOT NULL DEFAULT 0;
