ALTER TABLE customers ADD CONSTRAINT uq_customers_owner_pkid UNIQUE (created_by_pkid, pkid);

ALTER TABLE documents ADD COLUMN customer_pkid BIGINT;

UPDATE documents d
SET customer_pkid = c.pkid
FROM customers c
WHERE c.created_by_pkid = d.created_by_pkid
  AND c.account_no = d.customer_account_no;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_customer FOREIGN KEY (created_by_pkid, customer_pkid)
        REFERENCES customers (created_by_pkid, pkid) ON DELETE SET NULL (customer_pkid);

CREATE INDEX idx_documents_customer ON documents (customer_pkid);
