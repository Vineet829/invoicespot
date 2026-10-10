-- A schedule can leave each invoice as a draft to review instead of issuing
-- it. Every schedule so far issued its invoices, and only an invoice that is
-- issued can be emailed on its own.
ALTER TABLE recurring_schedules
    ADD COLUMN issue_automatically BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_recurring_email_needs_issue CHECK (issue_automatically OR NOT email_automatically);
