-- India GST mode, which a business turns on with a valid GSTIN. Each document
-- keeps the regime it was made in, and in GST mode the seller's state, the
-- place of supply, whether the supply is within the state, to another state
-- or an export (under a LUT or with IGST), and whether the recipient pays the
-- tax under reverse charge. Each rate's tax is kept split into CGST and SGST
-- or IGST. Every existing document and setting stays standard.
ALTER TABLE business_settings ADD COLUMN tax_regime VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN lut_reference VARCHAR(40),
    ADD CONSTRAINT chk_business_settings_regime CHECK (tax_regime IN ('STANDARD', 'GST_INDIA'));

ALTER TABLE documents ADD COLUMN tax_regime VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN seller_state_code VARCHAR(2), ADD COLUMN place_of_supply VARCHAR(2),
    ADD COLUMN supply_type VARCHAR(20), ADD COLUMN reverse_charge BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN lut_reference VARCHAR(40),
    ADD CONSTRAINT chk_documents_regime CHECK (tax_regime IN ('STANDARD', 'GST_INDIA')),
    ADD CONSTRAINT chk_documents_supply_type
        CHECK (supply_type IN ('INTRA', 'INTER', 'EXPORT_LUT', 'EXPORT_IGST')),
    ADD CONSTRAINT chk_documents_gst_issued CHECK (tax_regime = 'STANDARD' OR issued_at IS NULL
        OR (seller_state_code IS NOT NULL AND place_of_supply IS NOT NULL AND supply_type IS NOT NULL));

ALTER TABLE document_tax_summaries ADD COLUMN cgst NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN sgst NUMERIC(19, 2) NOT NULL DEFAULT 0, ADD COLUMN igst NUMERIC(19, 2) NOT NULL DEFAULT 0;
