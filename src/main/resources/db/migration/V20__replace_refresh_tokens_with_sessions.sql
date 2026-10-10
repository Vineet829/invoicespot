-- Each sign-in becomes a session. Its refresh token is kept only as a hash,
-- with the one before it for a short while, so two tabs refreshing at once
-- keep working. Stored refresh tokens go, so everyone signs in once more.
CREATE TABLE user_sessions (
    pkid          BIGSERIAL    PRIMARY KEY,
    id            UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_pkid     BIGINT       NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    previous_hash VARCHAR(64),
    rotated_at    TIMESTAMPTZ,
    ip            VARCHAR(64),
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_pkid);
CREATE INDEX idx_user_sessions_previous ON user_sessions (previous_hash) WHERE previous_hash IS NOT NULL;

DROP TABLE user_refresh_tokens;

ALTER TABLE audit_events ADD COLUMN session_id UUID;
