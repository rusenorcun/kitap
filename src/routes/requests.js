'use strict';

const express = require('express');
const db = require('../db');
const { requireRole, requireApprovedStudent } = require('../auth');
const { checkCanReceive } = require('../limits');

const router = express.Router();

const VALID_SOURCES = ['purchase', 'own'];

const REQUEST_SELECT = `
  SELECT r.*, u.name AS student_name, u.school_level, u.address, u.phone,
         b.title AS book_title, b.author AS book_author, b.cover_url, b.purchase_link,
         f.name AS fulfilled_by_name
  FROM requests r
  JOIN users u ON u.id = r.student_id
  JOIN books b ON b.id = r.book_id
  LEFT JOIN users f ON f.id = r.fulfilled_by
`;

// Açık istek listesi bağışçılara öğrenci adresini SIZDIRMAZ; adres yalnızca
// karşılayan bağışçıya /requests/fulfilled/mine üzerinden gösterilir.
function publicRequest(r) {
  const { address, phone, ...rest } = r;
  return rest;
}

// Öğrenci ihtiyaç duyduğu kitabı istek olarak listeler (kitap veri tabanından)
router.post('/', requireApprovedStudent, (req, res) => {
  const { book_id, description } = req.body || {};
  if (!book_id) return res.status(400).json({ error: 'Kitap (book_id) zorunludur.' });
  if (!db.prepare('SELECT id FROM books WHERE id = ?').get(book_id)) {
    return res.status(404).json({ error: 'Seçilen kitap bulunamadı.' });
  }
  const info = db
    .prepare('INSERT INTO requests (student_id, book_id, description) VALUES (?, ?, ?)')
    .run(req.user.id, book_id, description || null);
  res.status(201).json(publicRequest(db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(info.lastInsertRowid)));
});

// Açık istekleri listele (bağışçılar buradan karşılar) — adres gizli
router.get('/', (req, res) => {
  const status = req.query.status === 'all' ? null : (req.query.status || 'open');
  const rows = status
    ? db.prepare(`${REQUEST_SELECT} WHERE r.status = ? ORDER BY r.created_at DESC`).all(status)
    : db.prepare(`${REQUEST_SELECT} ORDER BY r.created_at DESC`).all();
  res.json(rows.map(publicRequest));
});

// Öğrencinin kendi istekleri
router.get('/mine', requireRole('student'), (req, res) => {
  const rows = db.prepare(`${REQUEST_SELECT} WHERE r.student_id = ? ORDER BY r.created_at DESC`).all(req.user.id);
  res.json(rows);
});

// Bağışçının karşıladığı istekler (öğrenci teslimat adresiyle birlikte)
router.get('/fulfilled/mine', requireRole('donor'), (req, res) => {
  const rows = db.prepare(`${REQUEST_SELECT} WHERE r.fulfilled_by = ? ORDER BY r.fulfilled_at DESC`).all(req.user.id);
  res.json(rows); // adres dahil — yalnızca karşılayan bağışçı görür
});

// Öğrenci açık isteğini siler
router.delete('/:id', requireRole('student'), (req, res) => {
  const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
  if (!reqRow || reqRow.student_id !== req.user.id) {
    return res.status(404).json({ error: 'İstek bulunamadı.' });
  }
  if (reqRow.status !== 'open') return res.status(409).json({ error: 'Karşılanmış bir istek silinemez.' });
  db.prepare('DELETE FROM requests WHERE id = ?').run(reqRow.id);
  res.json({ ok: true });
});

// Bağışçı bir öğrenci isteğini karşılar (satın alarak ya da elindeki kopyayla)
router.post('/:id/fulfill', requireRole('donor'), (req, res) => {
  const src = (req.body && req.body.source) || 'purchase';
  if (!VALID_SOURCES.includes(src)) {
    return res.status(400).json({ error: "Kaynak 'purchase' veya 'own' olmalıdır." });
  }

  const fulfill = db.transaction(() => {
    const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
    if (!reqRow) return { code: 404, body: { error: 'İstek bulunamadı.' } };
    if (reqRow.status !== 'open') return { code: 409, body: { error: 'Bu istek zaten karşılanmış.' } };

    // Kitap fiilen öğrenciye ulaşacağı için kotayı bu anda kontrol et.
    const check = checkCanReceive(reqRow.student_id);
    if (!check.ok) return { code: 403, body: { error: `Öğrenci kotası dolu: ${check.reason}`, quota: check.quota } };

    db.prepare(`UPDATE requests SET status = 'fulfilled', source = ?, fulfilled_by = ?, fulfilled_at = datetime('now')
                WHERE id = ?`).run(src, req.user.id, reqRow.id);
    return { code: 200, body: db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(reqRow.id) };
  });

  const result = fulfill();
  res.status(result.code).json(result.body); // karşılayan bağışçıya adres dahil döner
});

// Bağışçı kargoya verdi
router.post('/:id/ship', requireRole('donor'), (req, res) => {
  const reqRow = db.prepare('SELECT * FROM requests WHERE id = ? AND fulfilled_by = ?').get(req.params.id, req.user.id);
  if (!reqRow) return res.status(404).json({ error: 'İstek bulunamadı.' });
  if (reqRow.status !== 'fulfilled') return res.status(409).json({ error: 'Bu istek kargo aşamasında değil.' });
  db.prepare("UPDATE requests SET status = 'shipped', shipped_at = datetime('now') WHERE id = ?").run(reqRow.id);
  res.json({ ok: true, status: 'shipped' });
});

// Öğrenci teslim aldı
router.post('/:id/deliver', requireRole('student'), (req, res) => {
  const reqRow = db.prepare('SELECT * FROM requests WHERE id = ? AND student_id = ?').get(req.params.id, req.user.id);
  if (!reqRow) return res.status(404).json({ error: 'İstek bulunamadı.' });
  if (!['fulfilled', 'shipped'].includes(reqRow.status)) {
    return res.status(409).json({ error: 'Bu istek teslim aşamasında değil.' });
  }
  db.prepare("UPDATE requests SET status = 'delivered', delivered_at = datetime('now') WHERE id = ?").run(reqRow.id);
  res.json({ ok: true, status: 'delivered' });
});

module.exports = router;
