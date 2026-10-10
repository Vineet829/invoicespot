CREATE TABLE notifications (
    pkid          BIGSERIAL     PRIMARY KEY,
    id            UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_pkid     BIGINT        NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    document_pkid BIGINT        REFERENCES documents (pkid) ON DELETE CASCADE,
    type          VARCHAR(30)   NOT NULL,
    message       VARCHAR(300)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    read_at       TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user ON notifications (user_pkid, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_pkid) WHERE read_at IS NULL;
