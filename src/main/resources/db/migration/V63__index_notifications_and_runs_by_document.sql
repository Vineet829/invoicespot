-- Deleting a document deletes its notifications and clears it from the
-- recurring runs that made it. Without an index on the column pointing at
-- it, each of those reads the whole table.
CREATE INDEX idx_notifications_document ON notifications (document_pkid);
CREATE INDEX idx_recurring_runs_document ON recurring_runs (document_pkid);
