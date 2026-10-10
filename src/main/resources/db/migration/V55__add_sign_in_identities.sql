-- The Google or GitHub accounts each account can sign in with: at most one
-- per provider, and a provider's account opens exactly one account here. The
-- old Google ID column moves in; a blank one is dropped, and an ID two
-- accounts shared stays with the older account.
CREATE TABLE user_identities (
    pkid BIGSERIAL PRIMARY KEY,
    user_pkid BIGINT NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(254),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uq_user_identities_subject UNIQUE (provider, subject),
    CONSTRAINT uq_user_identities_user UNIQUE (user_pkid, provider)
);

INSERT INTO user_identities (user_pkid, provider, subject, email, linked_at)
SELECT DISTINCT ON (btrim(google_id)) pkid, 'google', btrim(google_id), email, updated_at
FROM users
WHERE google_id IS NOT NULL AND btrim(google_id) <> ''
ORDER BY btrim(google_id), pkid;

ALTER TABLE users DROP COLUMN google_id;
