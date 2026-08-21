'use strict';

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const DATA_DIR = path.join(__dirname, '..', 'data');
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

const dbPath = process.env.DB_PATH || path.join(DATA_DIR, 'kitap.db');
const db = new Database(dbPath);

db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
  -- Tek hesap modeli: her üye bağış yapabilir VE kitap alabilir (üye).
  -- Öğrenci = belgesi onaylanmış üye (öncelik + daha yüksek kota).
  CREATE TABLE IF NOT EXISTS users (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT NOT NULL,
    email          TEXT NOT NULL UNIQUE,
    password_hash  TEXT NOT NULL,
    is_admin       INTEGER NOT NULL DEFAULT 0,
    -- Öğrenci doğrulama durumu: 'none' (sadece üye) | 'pending' | 'approved' | 'rejected'
    student_status TEXT NOT NULL DEFAULT 'none'
                     CHECK (student_status IN ('none', 'pending', 'approved', 'rejected')),
    school_level   TEXT CHECK (school_level IN ('ortaokul', 'lise', 'universite')),
    document_no    TEXT UNIQUE,          -- Öğrenci belgesi numarası (tekil)
    document_path  TEXT,
    address        TEXT,                 -- Teslimat adresi (almak/takas için gerekli)
    phone          TEXT,
    blocked        INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- Merkezi kitap veri tabanı
  CREATE TABLE IF NOT EXISTS books (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT NOT NULL,
    author        TEXT,
    cover_url     TEXT,
    purchase_link TEXT,
    description   TEXT,
    created_by    INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );
  CREATE UNIQUE INDEX IF NOT EXISTS idx_books_title_author
    ON books (lower(title), lower(coalesce(author, '')));

  -- Bağışlar (öğrenci önceliği: created_at + 48 saat penceresi kodda uygulanır)
  CREATE TABLE IF NOT EXISTS donations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    donor_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       INTEGER NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    description   TEXT,
    quantity      INTEGER NOT NULL CHECK (quantity > 0),
    source        TEXT NOT NULL DEFAULT 'purchase' CHECK (source IN ('purchase', 'own')),
    target_level  TEXT NOT NULL DEFAULT 'hepsi'
                    CHECK (target_level IN ('ortaokul', 'lise', 'universite', 'hepsi')),
    status        TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS claims (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    donation_id   INTEGER NOT NULL REFERENCES donations(id) ON DELETE CASCADE,
    student_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        TEXT NOT NULL DEFAULT 'matched'
                    CHECK (status IN ('matched', 'shipped', 'delivered')),
    shipped_at    TEXT,
    delivered_at  TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (donation_id, student_id)
  );

  CREATE TABLE IF NOT EXISTS requests (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       INTEGER NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    description   TEXT,
    status        TEXT NOT NULL DEFAULT 'open'
                    CHECK (status IN ('open', 'fulfilled', 'shipped', 'delivered')),
    source        TEXT CHECK (source IN ('purchase', 'own')),
    fulfilled_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
    fulfilled_at  TEXT,
    shipped_at    TEXT,
    delivered_at  TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- TAKAS: üyenin takasa açtığı kitaplar
  CREATE TABLE IF NOT EXISTS swap_books (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       INTEGER NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    note          TEXT,                  -- karşılığında ne istediği
    status        TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (user_id, book_id)
  );

  -- TAKAS teklifleri: teklif eden kendi kitabını, hedef kitabın sahibine sunar
  CREATE TABLE IF NOT EXISTS swap_offers (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    offered_swap_book_id INTEGER NOT NULL REFERENCES swap_books(id) ON DELETE CASCADE,
    target_swap_book_id  INTEGER NOT NULL REFERENCES swap_books(id) ON DELETE CASCADE,
    message              TEXT,
    status               TEXT NOT NULL DEFAULT 'pending'
                           CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled', 'completed')),
    from_shipped_at      TEXT,
    to_shipped_at        TEXT,
    decided_at           TEXT,
    created_at           TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- Kullanıcı bildirimleri
  CREATE TABLE IF NOT EXISTS notifications (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          TEXT NOT NULL,
    message       TEXT NOT NULL,
    data          TEXT,
    is_read       INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read);
  CREATE INDEX IF NOT EXISTS idx_claims_student ON claims(student_id);
  CREATE INDEX IF NOT EXISTS idx_requests_student ON requests(student_id);
  CREATE INDEX IF NOT EXISTS idx_donations_donor ON donations(donor_id);
  CREATE INDEX IF NOT EXISTS idx_donations_book ON donations(book_id);
  CREATE INDEX IF NOT EXISTS idx_requests_book ON requests(book_id);
  CREATE INDEX IF NOT EXISTS idx_swap_books_user ON swap_books(user_id);
  CREATE INDEX IF NOT EXISTS idx_swap_offers_to ON swap_offers(to_user_id);
  CREATE INDEX IF NOT EXISTS idx_swap_offers_from ON swap_offers(from_user_id);
`);

module.exports = db;
