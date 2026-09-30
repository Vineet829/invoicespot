-- A business holds the details printed on its documents and the people who
-- work in it. Every account gets one, made from the business details its
-- profile kept until now, and owns it.
CREATE TABLE businesses (
    pkid         BIGSERIAL    PRIMARY KEY,
    id           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    legal_name   VARCHAR(255),
    email        VARCHAR(254),
    phone_number VARCHAR(30),
    address      VARCHAR(255),
    city         VARCHAR(255),
    state        VARCHAR(255),
    postal_code  VARCHAR(30),
    country      VARCHAR(255),
    logo         VARCHAR(500),
    tax_id       VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_businesses_name CHECK (btrim(name) <> '')
);

CREATE TABLE business_members (
    pkid           BIGSERIAL   PRIMARY KEY,
    business_pkid  BIGINT      NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    user_pkid      BIGINT      NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    role           VARCHAR(10) NOT NULL,
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMPTZ,
    CONSTRAINT uq_business_members_user UNIQUE (business_pkid, user_pkid),
    CONSTRAINT chk_business_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
);

-- A business has exactly one owner.
CREATE UNIQUE INDEX uq_business_members_owner ON business_members (business_pkid) WHERE role = 'OWNER';
CREATE INDEX idx_business_members_user ON business_members (user_pkid);

-- Named as documents already print the sender: the business name, else the
-- owner's full name. The email stays the owner's, as documents show today.
ALTER TABLE businesses ADD COLUMN founder_pkid BIGINT;
INSERT INTO businesses (name, email, phone_number, address, city, country, logo, created_at, updated_at, founder_pkid)
SELECT left(coalesce(nullif(btrim(u.business_name), ''),
                     nullif(btrim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')), ''),
                     u.username), 255),
       u.email, u.phone_number, u.address, u.city, u.country, u.avatar, u.created_at, u.updated_at, u.pkid
FROM users u
ORDER BY u.pkid;
INSERT INTO business_members (business_pkid, user_pkid, role, joined_at)
SELECT pkid, founder_pkid, 'OWNER', created_at FROM businesses;
ALTER TABLE businesses DROP COLUMN founder_pkid;

ALTER TABLE users
    DROP COLUMN business_name,
    DROP COLUMN phone_number,
    DROP COLUMN address,
    DROP COLUMN city,
    DROP COLUMN country,
    DROP COLUMN avatar;

-- The business a signed-in browser is working in.
ALTER TABLE user_sessions ADD COLUMN business_pkid BIGINT REFERENCES businesses (pkid) ON DELETE SET NULL;
CREATE INDEX idx_user_sessions_business ON user_sessions (business_pkid) WHERE business_pkid IS NOT NULL;
