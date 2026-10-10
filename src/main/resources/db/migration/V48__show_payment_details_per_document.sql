-- Whether an invoice shows the business's payment details while it is unpaid.
-- Every invoice made so far does.
ALTER TABLE documents ADD COLUMN show_payment_details BOOLEAN NOT NULL DEFAULT TRUE;
