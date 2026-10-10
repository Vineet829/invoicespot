CREATE TABLE users (
    pkid                BIGSERIAL PRIMARY KEY,
    id                  UUID         NOT NULL UNIQUE,
    email               VARCHAR(254) NOT NULL UNIQUE,
    username            VARCHAR(60)  NOT NULL UNIQUE,
    first_name          VARCHAR(60)  NOT NULL,
    last_name           VARCHAR(60)  NOT NULL,
    password            VARCHAR(255),
    is_email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    provider            VARCHAR(30)  NOT NULL DEFAULT 'email',
    google_id           VARCHAR(255),
    avatar              VARCHAR(500),
    business_name       VARCHAR(255),
    phone_number        VARCHAR(30),
    address             VARCHAR(255),
    city                VARCHAR(255),
    country             VARCHAR(255),
    password_changed_at TIMESTAMPTZ,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE user_roles (
    user_pkid BIGINT      NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    role      VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_pkid, role)
);

CREATE TABLE user_refresh_tokens (
    user_pkid BIGINT NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    token     TEXT   NOT NULL
);

CREATE INDEX idx_user_refresh_tokens_user ON user_refresh_tokens (user_pkid);

CREATE TABLE customers (
    pkid           BIGSERIAL PRIMARY KEY,
    id             UUID         NOT NULL UNIQUE,
    created_by_pkid BIGINT      NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(254) NOT NULL UNIQUE,
    account_no     VARCHAR(30),
    vat_tin_no     BIGINT       NOT NULL DEFAULT 0,
    address        VARCHAR(255),
    city           VARCHAR(255),
    country        VARCHAR(255),
    phone_number   VARCHAR(30)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_created_by ON customers (created_by_pkid);

CREATE TABLE documents (
    pkid                  BIGSERIAL PRIMARY KEY,
    id                    UUID         NOT NULL UNIQUE,
    created_by_pkid       BIGINT       NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    customer_name         VARCHAR(255),
    customer_email        VARCHAR(254),
    customer_account_no   VARCHAR(30),
    customer_vat_tin_no   VARCHAR(255),
    customer_address      VARCHAR(255),
    customer_city         VARCHAR(255),
    customer_country      VARCHAR(255),
    customer_phone_number VARCHAR(30),
    document_type         VARCHAR(20)  NOT NULL DEFAULT 'Invoice',
    document_number       VARCHAR(60),
    due_date              TIMESTAMPTZ,
    additional_info       TEXT,
    terms_conditions      TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'Not Paid',
    sub_total             DOUBLE PRECISION,
    sales_tax             DOUBLE PRECISION,
    rates                 VARCHAR(255),
    total                 DOUBLE PRECISION,
    currency              VARCHAR(10),
    total_amount_received DOUBLE PRECISION,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_documents_type CHECK (document_type IN ('Invoice', 'Receipt', 'Quotation')),
    CONSTRAINT chk_documents_status CHECK (status IN ('Paid', 'Not Fully Paid', 'Not Paid'))
);

CREATE INDEX idx_documents_created_by ON documents (created_by_pkid);

CREATE TABLE document_billing_items (
    document_pkid BIGINT           NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    position      INTEGER          NOT NULL,
    item_name     VARCHAR(255),
    unit_price    DOUBLE PRECISION,
    quantity      INTEGER,
    discount      VARCHAR(255),
    PRIMARY KEY (document_pkid, position)
);

CREATE TABLE document_payment_records (
    document_pkid   BIGINT           NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    position        INTEGER          NOT NULL,
    paid_by         VARCHAR(255),
    date_paid       VARCHAR(255),
    amount_paid     DOUBLE PRECISION,
    payment_method  VARCHAR(30),
    additional_info VARCHAR(255),
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    PRIMARY KEY (document_pkid, position),
    CONSTRAINT chk_payment_records_method CHECK (payment_method IN
        ('Cash', 'Mobile Money', 'PayPal', 'Credit Card', 'Bank Transfer', 'Others'))
);

CREATE TABLE verify_reset_tokens (
    pkid       BIGSERIAL PRIMARY KEY,
    id         UUID         NOT NULL UNIQUE,
    user_pkid  BIGINT       NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_verify_reset_tokens_user ON verify_reset_tokens (user_pkid);
