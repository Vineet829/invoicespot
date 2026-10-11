-- The business the demo's sample data was added to. Only a business marked so
-- is cleared when the demo renews, or gets the sample expenses and recurring
-- invoices, and nothing in the app changes the mark. A sample client marked it
-- before, which anyone trying the demo could delete or rename, and the
-- renewal then stopped for good.
ALTER TABLE businesses ADD COLUMN sample_data BOOLEAN NOT NULL DEFAULT FALSE;

-- A business that holds the sample data already has its sample clients, or
-- at least one of them that nobody removed or renamed.
UPDATE businesses b SET sample_data = TRUE
WHERE EXISTS (
    SELECT 1 FROM customers c
    WHERE c.business_pkid = b.pkid
      AND lower(c.email) IN (
          'accounts@harborandpine.example', 'billing@bluefin.example', 'hello@oakstreetbakery.example',
          'finance@lumenhealth.example', 'ap@tidewater.example', 'payables@atlasrobotics.example',
          'studio@juniperfilms.example', 'accounts@kestreloutdoor.example', 'team@marigoldevents.example',
          'billing@quillandco.example', 'ops@sablefitness.example', 'office@verdantfarms.example'));
