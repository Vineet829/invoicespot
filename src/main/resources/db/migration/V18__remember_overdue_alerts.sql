ALTER TABLE documents ADD COLUMN overdue_alerted_at TIMESTAMPTZ;

UPDATE documents
SET overdue_alerted_at = NOW()
WHERE document_type = 'Invoice' AND status <> 'Paid' AND due_date < NOW();
