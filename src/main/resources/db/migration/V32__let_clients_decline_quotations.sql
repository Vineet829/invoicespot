-- A client answers a quotation once: they accept it, or decline it, saying
-- why if they like. Only a quotation is declined.
ALTER TABLE documents
    ADD COLUMN declined_at    TIMESTAMPTZ,
    ADD COLUMN decline_reason VARCHAR(500),
    ADD CONSTRAINT chk_documents_answered_once CHECK (accepted_at IS NULL OR declined_at IS NULL),
    ADD CONSTRAINT chk_documents_declined_quotation CHECK (declined_at IS NULL OR document_type = 'Quotation');
