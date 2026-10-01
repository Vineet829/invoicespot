-- Reports can show every amount in one reporting currency, which starts as
-- the business's default currency. A document in another currency can keep
-- the rate typed in for it, "1 {currency} = x {base}", with the reporting
-- currency it was typed in against, so a later change of reporting currency
-- never converts an old document at the wrong rate.
ALTER TABLE business_settings ADD COLUMN base_currency VARCHAR(3);
UPDATE business_settings SET base_currency = default_currency;
ALTER TABLE business_settings ALTER COLUMN base_currency SET NOT NULL,
    ALTER COLUMN base_currency SET DEFAULT 'USD';

ALTER TABLE documents ADD COLUMN exchange_rate NUMERIC(20, 10), ADD COLUMN exchange_base VARCHAR(3),
    ADD CONSTRAINT chk_documents_exchange CHECK ((exchange_rate IS NULL AND exchange_base IS NULL)
        OR (exchange_rate > 0 AND exchange_base IS NOT NULL AND exchange_base <> currency));
