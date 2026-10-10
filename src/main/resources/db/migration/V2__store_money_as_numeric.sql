ALTER TABLE documents
    ALTER COLUMN sub_total TYPE NUMERIC(19, 2) USING round(sub_total::numeric, 2),
    ALTER COLUMN sales_tax TYPE NUMERIC(19, 2) USING round(sales_tax::numeric, 2),
    ALTER COLUMN total TYPE NUMERIC(19, 2) USING round(total::numeric, 2),
    ALTER COLUMN total_amount_received TYPE NUMERIC(19, 2) USING round(total_amount_received::numeric, 2),
    ALTER COLUMN rates TYPE NUMERIC(7, 4) USING (
        CASE WHEN rates ~ '^\s*[0-9]+(\.[0-9]+)?\s*$' THEN round(btrim(rates)::numeric, 4) ELSE 0 END);

ALTER TABLE documents
    ALTER COLUMN rates SET DEFAULT 0,
    ALTER COLUMN rates SET NOT NULL;

ALTER TABLE document_billing_items
    ALTER COLUMN unit_price TYPE NUMERIC(19, 4) USING round(unit_price::numeric, 4),
    ALTER COLUMN discount TYPE NUMERIC(7, 4) USING (
        CASE WHEN discount ~ '^\s*[0-9]+(\.[0-9]+)?\s*$' THEN round(btrim(discount)::numeric, 4) ELSE 0 END);

ALTER TABLE document_billing_items
    ALTER COLUMN discount SET DEFAULT 0,
    ALTER COLUMN discount SET NOT NULL;

ALTER TABLE document_payment_records
    ALTER COLUMN amount_paid TYPE NUMERIC(19, 2) USING round(amount_paid::numeric, 2);
