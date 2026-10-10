-- How clients can pay the business: a bank account, a UPI ID, an online
-- payment link and instructions. Unpaid invoices show whichever are set.
ALTER TABLE business_settings
    ADD COLUMN pay_account_holder VARCHAR(120),
    ADD COLUMN pay_bank_name VARCHAR(120),
    ADD COLUMN pay_account_number VARCHAR(34),
    ADD COLUMN pay_bank_code_type VARCHAR(12),
    ADD COLUMN pay_bank_code VARCHAR(40),
    ADD COLUMN pay_bank_code_label VARCHAR(30),
    ADD COLUMN pay_upi_id VARCHAR(100),
    ADD COLUMN pay_link_url VARCHAR(300),
    ADD COLUMN pay_instructions VARCHAR(1000),
    ADD CONSTRAINT chk_bs_bank_code_type
        CHECK (pay_bank_code_type IN ('IFSC', 'SWIFT', 'IBAN', 'SORT_CODE', 'ROUTING', 'BSB', 'OTHER')),
    ADD CONSTRAINT chk_bs_bank_code CHECK ((pay_bank_code IS NULL) = (pay_bank_code_type IS NULL)),
    ADD CONSTRAINT chk_bs_upi CHECK (pay_upi_id ~ '^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9.]{1,31}$'),
    ADD CONSTRAINT chk_bs_pay_link CHECK (pay_link_url LIKE 'https://%');
