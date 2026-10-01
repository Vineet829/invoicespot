-- The look of every PDF a business makes: Classic, as PDFs have looked so
-- far, Modern or Compact.
ALTER TABLE business_settings
    ADD COLUMN pdf_template VARCHAR(10) NOT NULL DEFAULT 'CLASSIC',
    ADD CONSTRAINT chk_business_settings_pdf_template CHECK (pdf_template IN ('CLASSIC', 'MODERN', 'COMPACT'));
