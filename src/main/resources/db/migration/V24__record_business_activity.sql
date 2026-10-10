-- Events about a business, such as changes to its team, name the business, so
-- its owner and admins can see what their team did. An event outlives its
-- business, keeping the name in its detail.
ALTER TABLE audit_events ADD COLUMN business_pkid BIGINT REFERENCES businesses (pkid) ON DELETE SET NULL;
CREATE INDEX idx_audit_events_business ON audit_events (business_pkid, occurred_at DESC)
    WHERE business_pkid IS NOT NULL;
