-- Rate-limit buckets shared by every instance of the app. The id is the
-- limit's name and a SHA-256 hash of what it limits, never an email or an
-- address. Bucket4j inserts a row before its state, so state can be empty;
-- expires_at (epoch milliseconds) is when the row can be removed, a little
-- after its bucket is full again.
CREATE TABLE rate_limit_buckets (
    id VARCHAR(100) PRIMARY KEY,
    state BYTEA,
    expires_at BIGINT
);

CREATE INDEX idx_rate_limit_buckets_expires_at ON rate_limit_buckets (expires_at);
