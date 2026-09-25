-- Yönetimin yayından kaldırdığı ilanlar sahibi tarafından yeniden açılamasın
ALTER TABLE donations ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE swap_books ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE;
