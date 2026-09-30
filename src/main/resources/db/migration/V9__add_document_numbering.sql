ALTER TABLE business_settings
    ADD COLUMN invoice_prefix        VARCHAR(20) NOT NULL DEFAULT 'INV-',
    ADD COLUMN quotation_prefix      VARCHAR(20) NOT NULL DEFAULT 'QUO-',
    ADD COLUMN receipt_prefix        VARCHAR(20) NOT NULL DEFAULT 'REC-',
    ADD COLUMN next_invoice_number   INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN next_quotation_number INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN next_receipt_number   INTEGER     NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_business_settings_next_numbers
        CHECK (next_invoice_number > 0 AND next_quotation_number > 0 AND next_receipt_number > 0);

CREATE UNIQUE INDEX uq_documents_owner_number ON documents (created_by_pkid, document_number);
