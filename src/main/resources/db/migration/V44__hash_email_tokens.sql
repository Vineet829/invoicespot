-- Verification and password reset tokens are kept as the hex SHA-256 of the
-- token in the link, as refresh tokens, share links and recovery codes are.
-- Links already sent keep working: their tokens are hashed in place.
UPDATE verify_reset_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');

CREATE INDEX idx_verify_reset_tokens_token ON verify_reset_tokens (token);
