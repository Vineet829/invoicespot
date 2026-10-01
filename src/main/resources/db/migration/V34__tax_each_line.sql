-- Each line has its own tax rate, and a document keeps the tax it charges at
-- each rate: what was taxable at that rate and the tax on it. Every line so
-- far was taxed at its document's one rate. Each document's summary is copied
-- from the amounts it already shows, not worked out again, so no total
-- changes; a document without lines keeps its rate this way too.
ALTER TABLE document_billing_items ADD COLUMN tax_rate NUMERIC(7, 4) NOT NULL DEFAULT 0;

UPDATE document_billing_items i
SET tax_rate = d.rates
FROM documents d
WHERE d.pkid = i.document_pkid;

CREATE TABLE document_tax_summaries (
    document_pkid BIGINT        NOT NULL REFERENCES documents (pkid) ON DELETE CASCADE,
    rate          NUMERIC(7, 4) NOT NULL,
    taxable       NUMERIC(19, 2) NOT NULL,
    tax           NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (document_pkid, rate),
    CONSTRAINT chk_tax_summaries_rate CHECK (rate >= 0)
);

INSERT INTO document_tax_summaries (document_pkid, rate, taxable, tax)
SELECT pkid, rates, sub_total, sales_tax FROM documents;

ALTER TABLE documents DROP COLUMN rates;
