-- An invitation to join a business with a role, sent by email. Only the
-- token's hash is kept. An email has at most one invitation waiting in each
-- business.
CREATE TABLE business_invitations (
    pkid             BIGSERIAL    PRIMARY KEY,
    id               UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    business_pkid    BIGINT       NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    email            VARCHAR(254) NOT NULL,
    role             VARCHAR(10)  NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    invited_by_pkid  BIGINT       REFERENCES users (pkid) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at          TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    accepted_at      TIMESTAMPTZ,
    accepted_by_pkid BIGINT       REFERENCES users (pkid) ON DELETE SET NULL,
    revoked_at       TIMESTAMPTZ,
    CONSTRAINT chk_business_invitations_role CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE UNIQUE INDEX uq_business_invitations_pending ON business_invitations (business_pkid, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
CREATE INDEX idx_business_invitations_business ON business_invitations (business_pkid, created_at DESC);
