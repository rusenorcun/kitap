'use strict';

const express = require('express');
const db = require('../db');
const { requireAuth, requireDeliverable } = require('../auth');
const { checkCanReceive } = require('../limits');
const { notify } = require('../notifications');
const { cleanStr } = require('../validate');

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

// Açık liste bağışçıya adres SIZDIRMAZ; adres yalnızca karşılayana gösterilir.
function publicRequest(r) {
  const { address, phone, ...rest } = r;
  return rest;
}

// Talep oluştur (üye veya öğrenci; adres gerekli)
router.post('/', requireDeliverable, (req, res) => {
  const { book_id } = req.body || {};
  const description = cleanStr(req.body && req.body.description, 500);
  if (!book_id) return res.status(400).json({ error: 'Kitap (book_id) zorunludur.' });
  if (!db.prepare('SELECT id FROM books WHERE id = ?').get(book_id)) {
    return res.status(404).json({ error: 'Seçilen kitap bulunamadı.' });
  }
  const info = db.prepare('INSERT INTO requests (student_id, book_id, description) VALUES (?, ?, ?)')
    .run(req.user.id, book_id, description);
  res.status(201).json(publicRequest(db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(info.lastInsertRowid)));
});

// Açık istekler (adres gizli). Filtreler: ?status= ?level= ?book_id= ?q=
router.get('/', (req, res) => {
  const { level, book_id, q } = req.query;
  const where = [];
  const params = {};
  const status = req.query.status === 'all' ? null : (req.query.status || 'open');
  if (status) { where.push('r.status = @status'); params.status = status; }
  if (level && ['ortaokul', 'lise', 'universite'].includes(level)) { where.push('u.school_level = @level'); params.level = level; }
  if (book_id) { where.push('r.book_id = @book_id'); params.book_id = Number(book_id); }
  if (q) { where.push('(b.title LIKE @like OR b.author LIKE @like)'); params.like = `%${q}%`; }

  const sql = `${REQUEST_SELECT} ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY r.created_at DESC`;
  res.json(db.prepare(sql).all(params).map(publicRequest));
});

router.get('/mine', requireAuth, (req, res) => {
  res.json(db.prepare(`${REQUEST_SELECT} WHERE r.student_id = ? ORDER BY r.created_at DESC`).all(req.user.id));
});

// Karşıladığım istekler (talep sahibinin adresiyle)
router.get('/fulfilled/mine', requireAuth, (req, res) => {
  res.json(db.prepare(`${REQUEST_SELECT} WHERE r.fulfilled_by = ? ORDER BY r.fulfilled_at DESC`).all(req.user.id));
});

router.delete('/:id', requireAuth, (req, res) => {
  const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
  if (!reqRow || reqRow.student_id !== req.user.id) return res.status(404).json({ error: 'İstek bulunamadı.' });
  if (reqRow.status !== 'open') return res.status(409).json({ error: 'Karşılanmış bir istek silinemez.' });
  db.prepare('DELETE FROM requests WHERE id = ?').run(reqRow.id);
  res.json({ ok: true });
});

// İsteği karşıla (herkes karşılayabilir; talep sahibinin kotası kontrol edilir)
router.post('/:id/fulfill', requireAuth, (req, res) => {
  const src = (req.body && req.body.source) || 'purchase';
  if (!VALID_SOURCES.includes(src)) return res.status(400).json({ error: "Kaynak 'purchase' veya 'own' olmalıdır." });

  const result = db.transaction(() => {
    const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
    if (!reqRow) return { code: 404, body: { error: 'İstek bulunamadı.' } };
    if (reqRow.status !== 'open') return { code: 409, body: { error: 'Bu istek zaten karşılanmış.' } };
    if (reqRow.student_id === req.user.id) return { code: 400, body: { error: 'Kendi isteğinizi karşılayamazsınız.' } };

    const student = db.prepare('SELECT id, student_status FROM users WHERE id = ?').get(reqRow.student_id);
    const check = checkCanReceive(student);
    if (!check.ok) return { code: 403, body: { error: `Alıcının kotası dolu: ${check.reason}`, quota: check.quota } };

    db.prepare(`UPDATE requests SET status = 'fulfilled', source = ?, fulfilled_by = ?, fulfilled_at = datetime('now') WHERE id = ?`)
      .run(src, req.user.id, reqRow.id);
    return { code: 200, body: db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(reqRow.id) };
  })();

  if (result.code === 200) {
    notify(result.body.student_id, 'request_fulfilled',
      `"${result.body.book_title}" isteğiniz ${req.user.name} tarafından karşılandı.`, { request_id: result.body.id });
  }
  res.status(result.code).json(result.body);
});

router.post('/:id/ship', requireAuth, (req, res) => {
  const reqRow = db.prepare(`SELECT r.*, b.title AS book_title FROM requests r JOIN books b ON b.id = r.book_id WHERE r.id = ? AND r.fulfilled_by = ?`)
    .get(req.params.id, req.user.id);
  if (!reqRow) return res.status(404).json({ error: 'İstek bulunamadı.' });
  if (reqRow.status !== 'fulfilled') return res.status(409).json({ error: 'Bu istek kargo aşamasında değil.' });
  db.prepare("UPDATE requests SET status = 'shipped', shipped_at = datetime('now') WHERE id = ?").run(reqRow.id);
  notify(reqRow.student_id, 'request_shipped', `"${reqRow.book_title}" kitabınız kargoya verildi.`, { request_id: reqRow.id });
  res.json({ ok: true, status: 'shipped' });
});

router.post('/:id/deliver', requireAuth, (req, res) => {
  const reqRow = db.prepare(`SELECT r.*, b.title AS book_title FROM requests r JOIN books b ON b.id = r.book_id WHERE r.id = ? AND r.student_id = ?`)
    .get(req.params.id, req.user.id);
  if (!reqRow) return res.status(404).json({ error: 'İstek bulunamadı.' });
  if (!['fulfilled', 'shipped'].includes(reqRow.status)) return res.status(409).json({ error: 'Bu istek teslim aşamasında değil.' });
  db.prepare("UPDATE requests SET status = 'delivered', delivered_at = datetime('now') WHERE id = ?").run(reqRow.id);
  if (reqRow.fulfilled_by) {
    notify(reqRow.fulfilled_by, 'request_delivered', `${req.user.name}, "${reqRow.book_title}" kitabını teslim aldı.`, { request_id: reqRow.id });
  }
  res.json({ ok: true, status: 'delivered' });
});

module.exports = router;
