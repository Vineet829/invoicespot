-- A two-factor secret is kept encrypted. It is set when setup starts and
-- counts only once enabled_at is set. The last time step used stops a code
-- from being used twice.
ALTER TABLE users ADD COLUMN two_factor_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN two_factor_enabled_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN two_factor_last_step BIGINT;

CREATE TABLE recovery_codes (
    pkid      BIGSERIAL    PRIMARY KEY,
    user_pkid BIGINT       NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    code_hash VARCHAR(64)  NOT NULL,
    used_at   TIMESTAMPTZ
);

CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_pkid);
