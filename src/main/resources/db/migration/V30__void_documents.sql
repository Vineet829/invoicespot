-- A document issued by mistake is voided rather than deleted: it keeps its
-- number, says why it was voided, and no longer counts toward anything owed.
-- Only an issued document can be void, and never without a reason.
ALTER TABLE documents
    ADD COLUMN voided_at   TIMESTAMPTZ,
    ADD COLUMN void_reason VARCHAR(500),
    ADD CONSTRAINT chk_documents_void CHECK (
        (voided_at IS NULL AND void_reason IS NULL)
        OR (voided_at IS NOT NULL AND issued_at IS NOT NULL AND btrim(void_reason) <> ''));
