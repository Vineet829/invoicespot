-- A payment keeps the receipt number it was first given, instead of taking its
-- place among the document's payments by date, which changed whenever a date
-- was edited or an earlier payment was recorded later. Existing payments keep
-- the numbers their receipts have shown so far.
--
-- Old payments of nothing are exempt from the amount check, which was added
-- NOT VALID. Numbering them rewrites their rows, which would check them, so
-- the check is set aside for the update and added back as it was.
ALTER TABLE payments DROP CONSTRAINT chk_payments_amount;
ALTER TABLE payments ADD COLUMN receipt_number INT;
UPDATE payments p
SET receipt_number = numbered.n
FROM (SELECT pkid, row_number() OVER (PARTITION BY document_pkid ORDER BY date_paid, pkid) AS n FROM payments) numbered
WHERE numbered.pkid = p.pkid;
ALTER TABLE payments ALTER COLUMN receipt_number SET NOT NULL,
    ADD CONSTRAINT uq_payments_receipt_number UNIQUE (document_pkid, receipt_number);
ALTER TABLE payments ADD CONSTRAINT chk_payments_amount
    CHECK (amount_paid >= 0 AND tds_amount >= 0 AND amount_paid + tds_amount > 0) NOT VALID;

-- The last receipt number a document gave, so a deleted payment's number
-- isn't given again.
ALTER TABLE documents ADD COLUMN receipts_numbered INT NOT NULL DEFAULT 0;
UPDATE documents d
SET receipts_numbered = numbered.n
FROM (SELECT document_pkid, max(receipt_number) AS n FROM payments GROUP BY document_pkid) numbered
WHERE numbered.document_pkid = d.pkid;
