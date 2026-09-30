CREATE TABLE document_events (
    pkid          BIGSERIAL    PRIMARY KEY,
    document_pkid BIGINT       NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    type          VARCHAR(30)  NOT NULL,
    detail        VARCHAR(500),
    payment_pkid  BIGINT       REFERENCES payments (pkid) ON DELETE SET NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_events_document ON document_events (document_pkid, occurred_at);
CREATE INDEX idx_document_events_payment ON document_events (payment_pkid);

INSERT INTO document_events (document_pkid, type, occurred_at)
SELECT pkid, 'CREATED', created_at
FROM documents;

INSERT INTO document_events (document_pkid, type, detail, payment_pkid, occurred_at)
SELECT payment.document_pkid,
       'PAYMENT_RECORDED',
       trim(coalesce(doc.currency, '') || ' ' || to_char(payment.amount_paid, 'FM999,999,999,999,990.00'))
           || ' · ' || payment.payment_method,
       payment.pkid,
       payment.created_at
FROM payments payment
JOIN documents doc ON doc.pkid = payment.document_pkid;
