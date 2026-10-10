CREATE TABLE audit_events (
    pkid        BIGSERIAL    PRIMARY KEY,
    id          UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    type        VARCHAR(40)  NOT NULL,
    user_pkid   BIGINT       REFERENCES users (pkid) ON DELETE SET NULL,
    actor_pkid  BIGINT       REFERENCES users (pkid) ON DELETE SET NULL,
    email       VARCHAR(254),
    detail      VARCHAR(255),
    ip          VARCHAR(64),
    user_agent  VARCHAR(255),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_user ON audit_events (user_pkid, occurred_at DESC);
CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at DESC);
