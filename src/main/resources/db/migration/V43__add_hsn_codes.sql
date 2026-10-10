-- The HSN code of goods, or SAC code of services, that GST invoices print on
-- each line and sum up by. Items keep one to fill in their lines. Neither is
-- required.
ALTER TABLE catalog_items ADD COLUMN hsn_sac VARCHAR(8),
    ADD CONSTRAINT chk_catalog_items_hsn CHECK (hsn_sac ~ '^[0-9]{4,8}$');
ALTER TABLE document_billing_items ADD COLUMN hsn_sac VARCHAR(8),
    ADD CONSTRAINT chk_billing_items_hsn CHECK (hsn_sac ~ '^[0-9]{4,8}$');
