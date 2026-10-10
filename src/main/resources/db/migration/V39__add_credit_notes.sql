-- A credit note reduces what is owed on an issued invoice or receipt. It is a
-- document of its own, numbered CN-0001 and on, that names the document it
-- credits; its amounts are stored positive. Each invoice and receipt keeps
-- the total of its issued credit notes.
ALTER TABLE documents DROP CONSTRAINT chk_documents_type;
ALTER TABLE documents ADD CONSTRAINT chk_documents_type
    CHECK (document_type IN ('Invoice', 'Receipt', 'Quotation', 'Credit Note'));
ALTER TABLE documents ADD COLUMN credited_pkid BIGINT, ADD COLUMN credit_reason VARCHAR(500),
    ADD COLUMN total_credited NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_documents_credited FOREIGN KEY (business_pkid, credited_pkid)
        REFERENCES documents (business_pkid, pkid),
    ADD CONSTRAINT chk_documents_credit_link
        CHECK ((document_type = 'Credit Note') = (credited_pkid IS NOT NULL));
CREATE INDEX idx_documents_credited ON documents (credited_pkid) WHERE credited_pkid IS NOT NULL;

ALTER TABLE business_settings ADD COLUMN credit_note_prefix VARCHAR(20) NOT NULL DEFAULT 'CN-',
    ADD COLUMN next_credit_note_number INTEGER NOT NULL DEFAULT 1,
    DROP CONSTRAINT chk_business_settings_next_numbers,
    ADD CONSTRAINT chk_business_settings_next_numbers CHECK (next_invoice_number > 0
        AND next_quotation_number > 0 AND next_receipt_number > 0 AND next_credit_note_number > 0);
