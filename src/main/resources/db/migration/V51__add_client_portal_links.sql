-- One link per client to a portal of their documents, balances and
-- statement. Like a share link, it is found by its token's hash, and kept
-- sealed with the app's key so it can be shown and sent again.
CREATE TABLE portal_links (
    pkid BIGSERIAL PRIMARY KEY,
    business_pkid BIGINT NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    customer_pkid BIGINT NOT NULL REFERENCES customers (pkid) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_sealed TEXT,
    created_by_pkid BIGINT REFERENCES users (pkid) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    last_viewed_at TIMESTAMPTZ,
    view_count INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_portal_links_active_customer ON portal_links (customer_pkid) WHERE revoked_at IS NULL;
