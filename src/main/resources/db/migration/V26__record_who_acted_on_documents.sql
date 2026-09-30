-- The member who did each thing to a document, for as long as their account
-- exists. Events recorded before this keep no actor: nobody noted one, so
-- none is guessed.
ALTER TABLE document_events ADD COLUMN actor_pkid BIGINT REFERENCES users (pkid) ON DELETE SET NULL;

CREATE INDEX idx_document_events_actor ON document_events (actor_pkid) WHERE actor_pkid IS NOT NULL;
