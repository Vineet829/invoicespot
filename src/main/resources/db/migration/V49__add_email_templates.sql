-- Each kind of email a business sends can have its own subject and message;
-- a kind without one sends the standard text. The one message a business
-- could write until now becomes its message for every kind it was used for,
-- everything but reminders, with the subject those emails had.
CREATE TABLE email_templates (
    pkid BIGSERIAL PRIMARY KEY,
    business_pkid BIGINT NOT NULL REFERENCES businesses (pkid) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_pkid BIGINT REFERENCES users (pkid) ON DELETE SET NULL,
    CONSTRAINT chk_email_templates_kind CHECK (kind IN (
        'INVOICE', 'QUOTATION', 'RECEIPT', 'CREDIT_NOTE', 'REMINDER', 'PAYMENT_RECEIPT', 'STATEMENT')),
    CONSTRAINT uq_email_templates_kind UNIQUE (business_pkid, kind)
);

INSERT INTO email_templates (business_pkid, kind, subject, body)
SELECT s.business_pkid, k.kind, '{document} from {business}', s.email_message
FROM business_settings s
CROSS JOIN (VALUES ('INVOICE'), ('QUOTATION'), ('RECEIPT'), ('CREDIT_NOTE'), ('PAYMENT_RECEIPT'), ('STATEMENT'))
    AS k (kind)
WHERE s.email_message ~ '\S';

ALTER TABLE business_settings DROP COLUMN email_message;
