-- A receipt is paid as it is made and a credit note takes its invoice's terms,
-- so neither has a due date, but ones made before that rule kept theirs and
-- still show them.
UPDATE documents SET due_date = NULL WHERE document_type IN ('Receipt', 'Credit Note') AND due_date IS NOT NULL;
