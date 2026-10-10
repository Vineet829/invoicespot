CREATE TABLE business_settings (
    pkid               BIGSERIAL     PRIMARY KEY,
    id                 UUID          NOT NULL UNIQUE,
    user_pkid          BIGINT        NOT NULL UNIQUE REFERENCES users (pkid) ON DELETE CASCADE,
    default_currency   VARCHAR(3)    NOT NULL DEFAULT 'USD',
    payment_terms_days INTEGER       NOT NULL DEFAULT 7,
    default_tax_rate   NUMERIC(7, 4) NOT NULL DEFAULT 0,
    tax_label          VARCHAR(20)   NOT NULL DEFAULT 'Tax',
    default_notes      TEXT,
    default_terms      TEXT,
    email_message      TEXT,
    time_zone          VARCHAR(64)   NOT NULL DEFAULT 'UTC',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_business_settings_terms CHECK (payment_terms_days BETWEEN 0 AND 365),
    CONSTRAINT chk_business_settings_tax CHECK (default_tax_rate BETWEEN 0 AND 100)
);
