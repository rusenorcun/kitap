-- Hesap e-postası doğrulaması. Mevcut hesaplar doğrulanmış sayılır (DEFAULT TRUE);
-- yeni kayıtlar uygulama tarafından FALSE ile açılır ve bağlantıyla doğrulanır.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

-- Yeni jeton türü ACCOUNT_VERIFY. Hibernate'in kurduğu eski bir veritabanında enum kısıtı
-- yeni değeri reddeder; Flyway ile kurulan şemada kısıt yoktur.
ALTER TABLE auth_tokens DROP CONSTRAINT IF EXISTS auth_tokens_type_check;
