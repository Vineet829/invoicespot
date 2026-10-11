-- A document made through the API without a currency had none, and the
-- clients CSV and the full export, which add up what is owed in each
-- currency, failed on it. Each such document takes its business's default
-- currency, as one made now does, and a document can't be without one again.
UPDATE documents d
SET currency = COALESCE(
        (SELECT s.default_currency FROM business_settings s WHERE s.business_pkid = d.business_pkid), 'USD')
WHERE d.currency IS NULL OR d.currency = '';

ALTER TABLE documents ALTER COLUMN currency SET NOT NULL;
