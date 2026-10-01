-- A client's GST number and the Indian state (or territory) they are in, as
-- GST codes them: "27" for Maharashtra, "96" for outside India. The state is
-- the GSTIN's first two digits when there is one. Each document keeps them as
-- they were when it was issued. VAT/TIN stays as it is: it is free text and
-- holds foreign VAT numbers.
ALTER TABLE customers ADD COLUMN gstin VARCHAR(15), ADD COLUMN state_code VARCHAR(2),
    ADD CONSTRAINT chk_customers_gstin CHECK (gstin ~ '^[0-9]{2}[0-9A-Z]{13}$'),
    ADD CONSTRAINT chk_customers_state_code CHECK (state_code ~ '^[0-9]{2}$'),
    ADD CONSTRAINT chk_customers_gstin_state CHECK (gstin IS NULL OR state_code = left(gstin, 2));

ALTER TABLE documents ADD COLUMN customer_gstin VARCHAR(15), ADD COLUMN customer_state_code VARCHAR(2);
