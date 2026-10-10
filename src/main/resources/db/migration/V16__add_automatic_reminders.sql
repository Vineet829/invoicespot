ALTER TABLE business_settings
    ADD COLUMN reminders_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reminder_days_before INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN reminder_on_due      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN reminder_every_days  INTEGER NOT NULL DEFAULT 7,
    ADD COLUMN reminder_max         INTEGER NOT NULL DEFAULT 3;
