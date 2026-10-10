-- Rate-limit buckets were kept under a plain SHA-256 of the address or email
-- they limit, which can be matched by trying every address. They are now kept
-- under a hash keyed with the app's secret, so the old rows are never looked
-- up again. They go now rather than as they expire, since a row whose first
-- write was cut short never expires. Every limit starts over once.
DELETE FROM rate_limit_buckets;
