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
  CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    role          TEXT NOT NULL CHECK (role IN ('donor', 'student', 'admin')),
    name          TEXT NOT NULL,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    -- Onay durumu: öğrenciler 'pending' başlar; donor/admin 'approved'
    status        TEXT NOT NULL DEFAULT 'approved'
                    CHECK (status IN ('pending', 'approved', 'rejected')),
    blocked       INTEGER NOT NULL DEFAULT 0,   -- 0/1 (admin engelleyebilir)
    -- Öğrenciye özel alanlar
    school_level  TEXT CHECK (school_level IN ('ortaokul', 'lise', 'universite')),
    document_no   TEXT UNIQUE,           -- Öğrenci belgesi numarası (tekil)
    document_path TEXT,                  -- Yüklenen belgenin dosya yolu
    address       TEXT,                  -- Teslimat adresi (öğrenci)
    phone         TEXT,                  -- İletişim telefonu (öğrenci)
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- Merkezi kitap veri tabanı. Bağış ve istekler buradan kitap seçer.
  CREATE TABLE IF NOT EXISTS books (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT NOT NULL,
    author        TEXT,
    cover_url     TEXT,                  -- Kapak görseli (linkten/elle/yüklenen)
    purchase_link TEXT,                  -- Alışveriş linki
    description   TEXT,
    created_by    INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );
  -- Aynı ad + aynı yazar ikinci kez oluşturulamaz (büyük/küçük harf duyarsız)
  CREATE UNIQUE INDEX IF NOT EXISTS idx_books_title_author
    ON books (lower(title), lower(coalesce(author, '')));

  -- 1. Bölüm: Bağışçının sunduğu kitaplar (öğrenciler talep eder)
  CREATE TABLE IF NOT EXISTS donations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    donor_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       INTEGER NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    description   TEXT,
    quantity      INTEGER NOT NULL CHECK (quantity > 0),
    -- 'purchase' = satın alıp gönderecek, 'own' = elindeki kopyayı gönderecek
    source        TEXT NOT NULL DEFAULT 'purchase' CHECK (source IN ('purchase', 'own')),
    target_level  TEXT NOT NULL DEFAULT 'hepsi'
                    CHECK (target_level IN ('ortaokul', 'lise', 'universite', 'hepsi')),
    status        TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- Bir öğrencinin bir bağıştan kitap alması + teslimat takibi
  CREATE TABLE IF NOT EXISTS claims (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    donation_id   INTEGER NOT NULL REFERENCES donations(id) ON DELETE CASCADE,
    student_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        TEXT NOT NULL DEFAULT 'matched'
                    CHECK (status IN ('matched', 'shipped', 'delivered')),
    shipped_at    TEXT,
    delivered_at  TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (donation_id, student_id)   -- aynı bağıştan bir öğrenci bir kez alır
  );

  -- 2. Bölüm: Öğrencinin istek olarak listelediği kitaplar (bağışçı karşılar)
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

  CREATE INDEX IF NOT EXISTS idx_claims_student ON claims(student_id);
  CREATE INDEX IF NOT EXISTS idx_requests_student ON requests(student_id);
  CREATE INDEX IF NOT EXISTS idx_donations_donor ON donations(donor_id);
  CREATE INDEX IF NOT EXISTS idx_donations_book ON donations(book_id);
  CREATE INDEX IF NOT EXISTS idx_requests_book ON requests(book_id);
`);

module.exports = db;
