-- A line's quantity can be a fraction, such as 1.5 hours, and a line can say
-- what it counts in. A line saved without a quantity already counted as 0,
-- so it becomes 0 and no total changes. Old quantities could be as large as
-- any integer, hence 15 digits; new ones are checked when they come in.
UPDATE document_billing_items SET quantity = 0 WHERE quantity IS NULL;

ALTER TABLE document_billing_items
    ALTER COLUMN quantity TYPE NUMERIC(15, 4) USING quantity::numeric,
    ALTER COLUMN quantity SET DEFAULT 0,
    ALTER COLUMN quantity SET NOT NULL,
    ADD COLUMN unit VARCHAR(20);
