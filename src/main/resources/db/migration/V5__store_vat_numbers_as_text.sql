ALTER TABLE customers
    ALTER COLUMN vat_tin_no DROP DEFAULT,
    ALTER COLUMN vat_tin_no DROP NOT NULL,
    ALTER COLUMN vat_tin_no TYPE VARCHAR(50) USING NULLIF(vat_tin_no, 0)::text;

UPDATE documents SET customer_vat_tin_no = NULL WHERE btrim(customer_vat_tin_no) IN ('', '0');
