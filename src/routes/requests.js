'use strict';

const express = require('express');
const db = require('../db');
const { requireRole } = require('../auth');
const { checkCanReceive } = require('../limits');

const router = express.Router();

const REQUEST_SELECT = `
  SELECT r.*, u.name AS student_name, u.school_level,
         f.name AS fulfilled_by_name
  FROM requests r
  JOIN users u ON u.id = r.student_id
  LEFT JOIN users f ON f.id = r.fulfilled_by
`;

// Öğrenci ihtiyaç duyduğu kitabı istek olarak listeler
router.post('/', requireRole('student'), (req, res) => {
  const { book_title, book_author, description } = req.body || {};
  if (!book_title) return res.status(400).json({ error: 'Kitap adı zorunludur.' });

  const info = db
    .prepare('INSERT INTO requests (student_id, book_title, book_author, description) VALUES (?, ?, ?, ?)')
    .run(req.user.id, book_title, book_author || null, description || null);
  const request = db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(info.lastInsertRowid);
  res.status(201).json(request);
});

// Açık istekleri listele (bağışçılar buradan satın alır)
router.get('/', (req, res) => {
  const status = req.query.status === 'all' ? null : (req.query.status || 'open');
  const rows = status
    ? db.prepare(`${REQUEST_SELECT} WHERE r.status = ? ORDER BY r.created_at DESC`).all(status)
    : db.prepare(`${REQUEST_SELECT} ORDER BY r.created_at DESC`).all();
  res.json(rows);
});

// Öğrencinin kendi istekleri
router.get('/mine', requireRole('student'), (req, res) => {
  const rows = db
    .prepare(`${REQUEST_SELECT} WHERE r.student_id = ? ORDER BY r.created_at DESC`)
    .all(req.user.id);
  res.json(rows);
});

// Öğrenci açık isteğini siler
router.delete('/:id', requireRole('student'), (req, res) => {
  const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
  if (!reqRow || reqRow.student_id !== req.user.id) {
    return res.status(404).json({ error: 'İstek bulunamadı.' });
  }
  if (reqRow.status !== 'open') {
    return res.status(409).json({ error: 'Karşılanmış bir istek silinemez.' });
  }
  db.prepare('DELETE FROM requests WHERE id = ?').run(reqRow.id);
  res.json({ ok: true });
});

// Bağışçı bir öğrenci isteğini satın alarak karşılar
router.post('/:id/fulfill', requireRole('donor'), (req, res) => {
  const fulfill = db.transaction(() => {
    const reqRow = db.prepare('SELECT * FROM requests WHERE id = ?').get(req.params.id);
    if (!reqRow) return { code: 404, body: { error: 'İstek bulunamadı.' } };
    if (reqRow.status !== 'open') return { code: 409, body: { error: 'Bu istek zaten karşılanmış.' } };

    // Kitap fiilen öğrenciye ulaşacağı için kotayı bu anda kontrol et.
    const check = checkCanReceive(reqRow.student_id);
    if (!check.ok) {
      return { code: 403, body: { error: `Öğrenci kotası dolu: ${check.reason}`, quota: check.quota } };
    }

    db.prepare(`UPDATE requests SET status = 'fulfilled', fulfilled_by = ?, fulfilled_at = datetime('now')
                WHERE id = ?`).run(req.user.id, reqRow.id);
    const updated = db.prepare(`${REQUEST_SELECT} WHERE r.id = ?`).get(reqRow.id);
    return { code: 200, body: updated };
  });

  const result = fulfill();
  res.status(result.code).json(result.body);
});

module.exports = router;
