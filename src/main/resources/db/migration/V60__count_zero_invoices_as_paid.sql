-- An invoice whose lines come to nothing counts as paid, but invoices made
-- before that rule still read Not Paid, and nothing can be paid on them to
-- change it. They are set as the app would set them now.
UPDATE documents d
SET status = 'Paid'
WHERE d.document_type = 'Invoice' AND d.total = 0 AND d.status <> 'Paid'
  AND EXISTS (SELECT 1 FROM document_billing_items i WHERE i.document_pkid = d.pkid);
