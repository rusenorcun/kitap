'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const db = require('../db');
const { requireAuth, requireRole } = require('../auth');
const { fetchMetadata } = require('../og');

const router = express.Router();

// Kapak görseli yüklemeleri
const COVER_DIR = path.join(__dirname, '..', '..', 'uploads', 'covers');
if (!fs.existsSync(COVER_DIR)) fs.mkdirSync(COVER_DIR, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, COVER_DIR),
  filename: (req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    cb(null, `${Date.now()}-${Math.round(Math.random() * 1e9)}-${safe}`);
  },
});
const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const ok = /^image\/(png|jpe?g|webp|gif)$/.test(file.mimetype);
    cb(ok ? null : new Error('Kapak yalnızca görsel (PNG/JPG/WEBP/GIF) olabilir.'), ok);
  },
});

function findExisting(title, author) {
  return db
    .prepare('SELECT * FROM books WHERE lower(title) = lower(?) AND lower(coalesce(author, \'\')) = lower(?)')
    .get(title, author || '');
}

// Linkten üst veri önizleme (oluşturmadan önce kullanıcı doğrulasın)
router.post('/preview', requireAuth, async (req, res) => {
  const { url } = req.body || {};
  if (!url) return res.status(400).json({ error: 'Bağlantı (url) zorunludur.' });
  try {
    const meta = await fetchMetadata(url);
    res.json(meta);
  } catch (err) {
    res.status(502).json({ error: err.message || 'Bağlantıdan veri alınamadı.' });
  }
});

// Kitap arama / listeleme
router.get('/', (req, res) => {
  const q = (req.query.q || '').trim();
  const rows = q
    ? db
        .prepare(`SELECT * FROM books WHERE title LIKE @like OR author LIKE @like
                  ORDER BY title LIMIT 100`)
        .all({ like: `%${q}%` })
    : db.prepare('SELECT * FROM books ORDER BY created_at DESC LIMIT 100').all();
  res.json(rows);
});

router.get('/:id', (req, res) => {
  const book = db.prepare('SELECT * FROM books WHERE id = ?').get(req.params.id);
  if (!book) return res.status(404).json({ error: 'Kitap bulunamadı.' });
  res.json(book);
});

// Yüklenen kapak görselini sun (genel erişim)
router.get('/cover/:filename', (req, res) => {
  const file = path.basename(req.params.filename);
  const full = path.join(COVER_DIR, file);
  if (!fs.existsSync(full)) return res.status(404).json({ error: 'Kapak bulunamadı.' });
  res.sendFile(full);
});

// Kitap oluştur (varsa mevcut kaydı döndürür) — find-or-create.
// Aynı ad + aynı yazar ikinci kez oluşturulamaz.
// Link verilirse eksik başlık/kapak OpenGraph'tan otomatik doldurulur.
router.post('/', requireAuth, upload.single('cover'), async (req, res) => {
  let { title, author, cover_url, purchase_link, description } = req.body || {};

  try {
    // Link varsa ve başlık/kapak eksikse, linkten doldurmayı dene
    if (purchase_link && (!title || !cover_url)) {
      try {
        const meta = await fetchMetadata(purchase_link);
        if (!title && meta.title) title = meta.title;
        if (!cover_url && meta.image) cover_url = meta.image;
        if (!author && meta.author) author = meta.author;
        if (!description && meta.description) description = meta.description;
      } catch (_err) {
        // Otomatik doldurma başarısızsa elle verilen alanlarla devam et
      }
    }

    if (req.file) {
      cover_url = `/api/books/cover/${req.file.filename}`;
    }

    if (!title) {
      if (req.file) fs.promises.unlink(req.file.path).catch(() => {});
      return res.status(400).json({
        error: 'Kitap adı gerekli. Geçerli bir alışveriş linki verin veya başlığı elle girin.',
      });
    }

    const existing = findExisting(title, author);
    if (existing) {
      // Yeni kapak yüklendiyse ama kitap zaten varsa, gereksiz dosyayı sil
      if (req.file) fs.promises.unlink(req.file.path).catch(() => {});
      return res.status(200).json({ book: existing, existed: true });
    }

    const info = db
      .prepare(`INSERT INTO books (title, author, cover_url, purchase_link, description, created_by)
                VALUES (?, ?, ?, ?, ?, ?)`)
      .run(title, author || null, cover_url || null, purchase_link || null, description || null, req.user.id);
    const book = db.prepare('SELECT * FROM books WHERE id = ?').get(info.lastInsertRowid);
    res.status(201).json({ book, existed: false });
  } catch (err) {
    if (req.file) fs.promises.unlink(req.file.path).catch(() => {});
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      const existing = findExisting(title, author);
      if (existing) return res.status(200).json({ book: existing, existed: true });
    }
    throw err;
  }
});

// Kitap güncelle (yalnızca admin) — örn. eksik yazar/kapak düzeltme
router.patch('/:id', requireRole('admin'), (req, res) => {
  const book = db.prepare('SELECT * FROM books WHERE id = ?').get(req.params.id);
  if (!book) return res.status(404).json({ error: 'Kitap bulunamadı.' });
  const fields = ['title', 'author', 'cover_url', 'purchase_link', 'description'];
  const updates = {};
  for (const f of fields) if (f in (req.body || {})) updates[f] = req.body[f];
  if (!Object.keys(updates).length) return res.status(400).json({ error: 'Güncellenecek alan yok.' });
  const setSql = Object.keys(updates).map((k) => `${k} = @${k}`).join(', ');
  db.prepare(`UPDATE books SET ${setSql} WHERE id = @id`).run({ ...updates, id: book.id });
  res.json(db.prepare('SELECT * FROM books WHERE id = ?').get(book.id));
});

module.exports = router;
