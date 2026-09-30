ALTER TABLE documents ADD CONSTRAINT uq_documents_owner_pkid UNIQUE (created_by_pkid, pkid);

ALTER TABLE documents ADD COLUMN source_pkid BIGINT;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_source FOREIGN KEY (created_by_pkid, source_pkid)
        REFERENCES documents (created_by_pkid, pkid) ON DELETE SET NULL (source_pkid);

CREATE INDEX idx_documents_source ON documents (source_pkid);
