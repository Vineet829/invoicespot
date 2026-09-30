ALTER TABLE customers DROP CONSTRAINT customers_email_key;

CREATE UNIQUE INDEX uq_customers_owner_email ON customers (created_by_pkid, lower(email));
CREATE UNIQUE INDEX uq_customers_owner_account_no ON customers (created_by_pkid, account_no);
