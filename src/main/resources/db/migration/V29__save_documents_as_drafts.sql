-- A document is a draft until it is issued. A draft has no number, may have
-- no issue date yet (it is then dated the day it is issued), and keeps no
-- seller details or tax name: it shows the business as it is until it goes
-- out. Every document so far was issued when it was made.
ALTER TABLE documents ADD COLUMN issued_at TIMESTAMPTZ;
UPDATE documents SET issued_at = created_at;

ALTER TABLE documents
    ALTER COLUMN issue_date DROP NOT NULL,
    ALTER COLUMN tax_label DROP NOT NULL,
    ALTER COLUMN tax_label DROP DEFAULT,
    ADD CONSTRAINT chk_documents_issued_kept
        CHECK (issued_at IS NULL OR (issue_date IS NOT NULL AND seller_name IS NOT NULL AND tax_label IS NOT NULL));

CREATE INDEX idx_documents_business_drafts ON documents (business_pkid) WHERE issued_at IS NULL;
