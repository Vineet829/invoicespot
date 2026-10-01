-- The locks scheduled jobs take so that, with several app instances, only one
-- of them runs a job each time it is due. The layout is the one ShedLock
-- expects; its times are UTC, read from the database's clock.
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
