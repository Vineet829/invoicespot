-- An item can carry the tax rate and unit its lines usually have. An item
-- without a rate leaves new lines at the business's default rate.
ALTER TABLE catalog_items
    ADD COLUMN tax_rate NUMERIC(7, 4),
    ADD COLUMN unit     VARCHAR(20),
    ADD CONSTRAINT chk_catalog_items_tax_rate CHECK (tax_rate BETWEEN 0 AND 100);
