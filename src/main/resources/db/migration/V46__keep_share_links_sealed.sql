-- A share link's token is also kept sealed with the app's encryption key, so
-- the link can be shown again and put in emails. Its hash still finds it, and
-- a copy of the database alone still can't open it. Links made before this
-- have none, and can only be replaced.
ALTER TABLE share_links ADD COLUMN token_sealed TEXT;
