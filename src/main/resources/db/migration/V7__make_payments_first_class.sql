CREATE TABLE payments (
    pkid            BIGSERIAL      PRIMARY KEY,
    id              UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    document_pkid   BIGINT         NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    paid_by         VARCHAR(255),
    date_paid       DATE           NOT NULL,
    amount_paid     NUMERIC(19, 2) NOT NULL,
    payment_method  VARCHAR(30)    NOT NULL DEFAULT 'Cash',
    additional_info VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payments_method CHECK (payment_method IN
        ('Cash', 'Mobile Money', 'PayPal', 'Credit Card', 'Bank Transfer', 'Others'))
);

INSERT INTO payments (document_pkid, paid_by, date_paid, amount_paid, payment_method, additional_info,
                      created_at, updated_at)
SELECT record.document_pkid,
       record.paid_by,
       CASE
           WHEN record.date_paid ~ '^\d{4}-\d{2}-\d{2}$' THEN record.date_paid::date
           WHEN record.date_paid ~ '^\d{4}-\d{2}-\d{2}T' THEN (record.date_paid::timestamptz AT TIME ZONE 'UTC')::date
           ELSE coalesce(record.created_at, doc.created_at)::date
       END,
       coalesce(record.amount_paid, 0),
       coalesce(record.payment_method, 'Cash'),
       record.additional_info,
       coalesce(record.created_at, doc.created_at),
       coalesce(record.updated_at, record.created_at, doc.created_at)
FROM document_payment_records record
JOIN documents doc ON doc.pkid = record.document_pkid
ORDER BY record.document_pkid, record.position;

ALTER TABLE payments ADD CONSTRAINT chk_payments_amount CHECK (amount_paid > 0) NOT VALID;

CREATE INDEX idx_payments_document ON payments (document_pkid);

DROP TABLE document_payment_records;
