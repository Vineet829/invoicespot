CREATE TABLE share_links (
    pkid           BIGSERIAL    PRIMARY KEY,
    document_pkid  BIGINT       NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    token_hash     VARCHAR(64)  NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at     TIMESTAMPTZ,
    last_viewed_at TIMESTAMPTZ,
    view_count     INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_share_links_active_document ON share_links (document_pkid) WHERE revoked_at IS NULL;

ALTER TABLE documents ADD COLUMN accepted_at TIMESTAMPTZ;
