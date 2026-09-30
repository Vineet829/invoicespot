CREATE TABLE recurring_schedules (
    pkid                BIGSERIAL    PRIMARY KEY,
    id                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    created_by_pkid     BIGINT       NOT NULL REFERENCES users (pkid) ON DELETE CASCADE,
    template_pkid       BIGINT       NOT NULL UNIQUE,
    frequency           VARCHAR(20)  NOT NULL,
    start_date          DATE         NOT NULL,
    next_run            DATE,
    end_date            DATE,
    max_runs            INTEGER,
    runs_done           INTEGER      NOT NULL DEFAULT 0,
    start_offset        INTEGER      NOT NULL DEFAULT 0,
    email_automatically BOOLEAN      NOT NULL DEFAULT FALSE,
    paused              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_recurring_frequency CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT fk_recurring_template FOREIGN KEY (created_by_pkid, template_pkid)
        REFERENCES documents (created_by_pkid, pkid) ON DELETE CASCADE
);

CREATE INDEX idx_recurring_schedules_owner ON recurring_schedules (created_by_pkid);
CREATE INDEX idx_recurring_schedules_due ON recurring_schedules (next_run) WHERE NOT paused;

CREATE TABLE recurring_runs (
    pkid          BIGSERIAL    PRIMARY KEY,
    schedule_pkid BIGINT       NOT NULL REFERENCES recurring_schedules (pkid) ON DELETE CASCADE,
    run_date      DATE         NOT NULL,
    document_pkid BIGINT       REFERENCES documents (pkid) ON DELETE SET NULL,
    emailed       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recurring_runs_period UNIQUE (schedule_pkid, run_date)
);
