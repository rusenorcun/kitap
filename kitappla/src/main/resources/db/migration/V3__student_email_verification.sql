ALTER TABLE auth_tokens ADD COLUMN IF NOT EXISTS verification_email VARCHAR(255);
