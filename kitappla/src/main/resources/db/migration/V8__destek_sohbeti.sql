-- Yönetime doğrudan mesaj için yeni sohbet türü: SUPPORT (ref_id = üyenin kimliği).
-- Şema Flyway ile kurulduysa kind sütununda kısıt yoktur; Hibernate'in kurduğu
-- eski bir veritabanında ise enum kısıtı yeni değeri reddeder, o yüzden kaldırılır.
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_kind_check;
