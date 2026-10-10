CREATE TABLE catalog_items (
    pkid            BIGSERIAL      PRIMARY KEY,
    id              UUID           NOT NULL UNIQUE,
    created_by_pkid BIGINT         NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    name            VARCHAR(255)   NOT NULL,
    description     VARCHAR(1000),
    unit_price      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_catalog_items_price CHECK (unit_price >= 0)
);

CREATE UNIQUE INDEX uq_catalog_items_owner_name ON catalog_items (created_by_pkid, lower(name));
