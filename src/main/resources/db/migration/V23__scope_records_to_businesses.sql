-- Clients, documents, items, recurring schedules, settings and notifications
-- belong to a business from now on. Until now every account owned exactly one
-- business, made from it, so each record goes to its author's business. The
-- author is kept, and forgotten if their account is deleted; the records go
-- only with their business.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM business_members WHERE role = 'OWNER' GROUP BY user_pkid HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'An account owns more than one business, so its records have no single business to join';
    END IF;
END $$;

ALTER TABLE customers ADD COLUMN business_pkid BIGINT;
ALTER TABLE documents ADD COLUMN business_pkid BIGINT;
ALTER TABLE catalog_items ADD COLUMN business_pkid BIGINT;
ALTER TABLE recurring_schedules ADD COLUMN business_pkid BIGINT;
ALTER TABLE business_settings ADD COLUMN business_pkid BIGINT;
ALTER TABLE notifications ADD COLUMN business_pkid BIGINT;

UPDATE customers x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.created_by_pkid AND m.role = 'OWNER';
UPDATE documents x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.created_by_pkid AND m.role = 'OWNER';
UPDATE catalog_items x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.created_by_pkid AND m.role = 'OWNER';
UPDATE recurring_schedules x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.created_by_pkid AND m.role = 'OWNER';
UPDATE business_settings x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.user_pkid AND m.role = 'OWNER';
UPDATE notifications x SET business_pkid = m.business_pkid
FROM business_members m WHERE m.user_pkid = x.user_pkid AND m.role = 'OWNER';

ALTER TABLE customers ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_customers_business FOREIGN KEY (business_pkid) REFERENCES businesses (pkid) ON DELETE CASCADE;
ALTER TABLE documents ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_documents_business FOREIGN KEY (business_pkid) REFERENCES businesses (pkid) ON DELETE CASCADE;
ALTER TABLE catalog_items ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_catalog_items_business FOREIGN KEY (business_pkid) REFERENCES businesses (pkid) ON DELETE CASCADE;
ALTER TABLE recurring_schedules ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_recurring_schedules_business FOREIGN KEY (business_pkid)
        REFERENCES businesses (pkid) ON DELETE CASCADE;
ALTER TABLE business_settings ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_business_settings_business FOREIGN KEY (business_pkid)
        REFERENCES businesses (pkid) ON DELETE CASCADE;
ALTER TABLE notifications ALTER COLUMN business_pkid SET NOT NULL,
    ADD CONSTRAINT fk_notifications_business FOREIGN KEY (business_pkid) REFERENCES businesses (pkid) ON DELETE CASCADE;

-- A document's client and source, and a schedule's template, stay in one
-- business, as they stayed with one owner.
ALTER TABLE recurring_schedules DROP CONSTRAINT fk_recurring_template;
ALTER TABLE documents DROP CONSTRAINT fk_documents_customer, DROP CONSTRAINT fk_documents_source;
ALTER TABLE customers DROP CONSTRAINT uq_customers_owner_pkid;
ALTER TABLE documents DROP CONSTRAINT uq_documents_owner_pkid;
ALTER TABLE customers ADD CONSTRAINT uq_customers_business_pkid UNIQUE (business_pkid, pkid);
ALTER TABLE documents ADD CONSTRAINT uq_documents_business_pkid UNIQUE (business_pkid, pkid);
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_customer FOREIGN KEY (business_pkid, customer_pkid)
        REFERENCES customers (business_pkid, pkid) ON DELETE SET NULL (customer_pkid),
    ADD CONSTRAINT fk_documents_source FOREIGN KEY (business_pkid, source_pkid)
        REFERENCES documents (business_pkid, pkid) ON DELETE SET NULL (source_pkid);
ALTER TABLE recurring_schedules
    ADD CONSTRAINT fk_recurring_template FOREIGN KEY (business_pkid, template_pkid)
        REFERENCES documents (business_pkid, pkid) ON DELETE CASCADE;

-- Client emails and account numbers, document numbers and item names are
-- unique within a business.
DROP INDEX uq_customers_owner_email;
DROP INDEX uq_customers_owner_account_no;
DROP INDEX uq_documents_owner_number;
DROP INDEX uq_catalog_items_owner_name;
CREATE UNIQUE INDEX uq_customers_business_email ON customers (business_pkid, lower(email));
CREATE UNIQUE INDEX uq_customers_business_account_no ON customers (business_pkid, account_no);
CREATE UNIQUE INDEX uq_documents_business_number ON documents (business_pkid, document_number);
CREATE UNIQUE INDEX uq_catalog_items_business_name ON catalog_items (business_pkid, lower(name));
CREATE INDEX idx_recurring_schedules_business ON recurring_schedules (business_pkid);
ALTER INDEX idx_recurring_schedules_owner RENAME TO idx_recurring_schedules_created_by;
CREATE INDEX idx_catalog_items_created_by ON catalog_items (created_by_pkid);

ALTER TABLE customers DROP CONSTRAINT customers_created_by_pkid_fkey,
    ALTER COLUMN created_by_pkid DROP NOT NULL,
    ADD CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by_pkid) REFERENCES users (pkid) ON DELETE SET NULL;
ALTER TABLE documents DROP CONSTRAINT documents_created_by_pkid_fkey,
    ALTER COLUMN created_by_pkid DROP NOT NULL,
    ADD CONSTRAINT fk_documents_created_by FOREIGN KEY (created_by_pkid) REFERENCES users (pkid) ON DELETE SET NULL;
ALTER TABLE catalog_items DROP CONSTRAINT catalog_items_created_by_pkid_fkey,
    ALTER COLUMN created_by_pkid DROP NOT NULL,
    ADD CONSTRAINT fk_catalog_items_created_by FOREIGN KEY (created_by_pkid)
        REFERENCES users (pkid) ON DELETE SET NULL;
ALTER TABLE recurring_schedules DROP CONSTRAINT recurring_schedules_created_by_pkid_fkey,
    ALTER COLUMN created_by_pkid DROP NOT NULL,
    ADD CONSTRAINT fk_recurring_schedules_created_by FOREIGN KEY (created_by_pkid)
        REFERENCES users (pkid) ON DELETE SET NULL;

-- One settings row per business.
ALTER TABLE business_settings ADD CONSTRAINT uq_business_settings_business UNIQUE (business_pkid);
ALTER TABLE business_settings DROP COLUMN user_pkid;

-- A notification is for one member, in one business.
DROP INDEX idx_notifications_user;
DROP INDEX idx_notifications_unread;
CREATE INDEX idx_notifications_member ON notifications (user_pkid, business_pkid, created_at DESC);
CREATE INDEX idx_notifications_member_unread ON notifications (user_pkid, business_pkid) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_business ON notifications (business_pkid);
