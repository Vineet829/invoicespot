-- Bucket4j writes a bucket in two steps: it inserts the id alone, then sets
-- the state and expires_at. A row whose second step never came (the instance
-- stopped, or a database call failed in between) had no expiry, so the daily
-- cleanup never removed it. Such a row now expires a day after it was written
-- (86400000 milliseconds); a write that completes sets its own expiry over it.
ALTER TABLE rate_limit_buckets
    ALTER COLUMN expires_at SET DEFAULT (extract(epoch FROM now()) * 1000)::bigint + 86400000;

UPDATE rate_limit_buckets SET expires_at = DEFAULT WHERE expires_at IS NULL;
