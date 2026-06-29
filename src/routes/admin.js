'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireRole } = require('../auth');

const router = express.Router();
const DOC_DIR = path.join(__dirname, '..', '..', 'uploads', 'documents');

// Tüm admin uçları admin rolü gerektirir
router.use(requireRole('admin'));

const USER_COLUMNS = `id, role, name, email, status, blocked, school_level, document_no,
                      address, phone, created_at`;

// Genel istatistikler
router.get('/stats', (req, res) => {
  const one = (sql) => db.prepare(sql).get().n;
  res.json({
    users: one('SELECT COUNT(*) AS n FROM users'),
    donors: one("SELECT COUNT(*) AS n FROM users WHERE role = 'donor'"),
    students: one("SELECT COUNT(*) AS n FROM users WHERE role = 'student'"),
    pendingStudents: one("SELECT COUNT(*) AS n FROM users WHERE role = 'student' AND status = 'pending'"),
    blocked: one('SELECT COUNT(*) AS n FROM users WHERE blocked = 1'),
    books: one('SELECT COUNT(*) AS n FROM books'),
    donations: one('SELECT COUNT(*) AS n FROM donations'),
    requests: one('SELECT COUNT(*) AS n FROM requests'),
    claims: one('SELECT COUNT(*) AS n FROM claims'),
  });
});

// Kullanıcıları listele (filtre: ?role= ?status= ?blocked=)
router.get('/users', (req, res) => {
  const { role, status, blocked } = req.query;
  const where = [];
  const params = {};
  if (role) { where.push('role = @role'); params.role = role; }
  if (status) { where.push('status = @status'); params.status = status; }
  if (blocked !== undefined) { where.push('blocked = @blocked'); params.blocked = blocked === 'true' || blocked === '1' ? 1 : 0; }
  const sql = `SELECT ${USER_COLUMNS} FROM users ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY created_at DESC`;
  res.json(db.prepare(sql).all(params));
});

router.get('/users/:id', (req, res) => {
  const user = db.prepare(`SELECT ${USER_COLUMNS} FROM users WHERE id = ?`).get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  res.json(user);
});

// Öğrenci belgesini görüntüle/indir
router.get('/users/:id/document', (req, res) => {
  const user = db.prepare('SELECT role, document_path FROM users WHERE id = ?').get(req.params.id);
  if (!user || user.role !== 'student' || !user.document_path) {
    return res.status(404).json({ error: 'Belge bulunamadı.' });
  }
  const full = path.join(DOC_DIR, path.basename(user.document_path));
  if (!fs.existsSync(full)) return res.status(404).json({ error: 'Belge dosyası bulunamadı.' });
  res.sendFile(full);
});

function setStatus(req, res, status) {
  const user = db.prepare("SELECT id, role FROM users WHERE id = ?").get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  if (user.role !== 'student') return res.status(400).json({ error: 'Yalnızca öğrenci kayıtları onaylanır/reddedilir.' });
  db.prepare('UPDATE users SET status = ? WHERE id = ?').run(status, user.id);
  res.json({ ok: true, status });
}

// Belge onayla / reddet
router.post('/users/:id/approve', (req, res) => setStatus(req, res, 'approved'));
router.post('/users/:id/reject', (req, res) => setStatus(req, res, 'rejected'));

// Engelle / engeli kaldır
router.post('/users/:id/block', (req, res) => {
  const user = db.prepare('SELECT id, role FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  if (user.id === req.user.id) return res.status(400).json({ error: 'Kendinizi engelleyemezsiniz.' });
  db.prepare('UPDATE users SET blocked = 1 WHERE id = ?').run(user.id);
  res.json({ ok: true, blocked: true });
});

router.post('/users/:id/unblock', (req, res) => {
  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  db.prepare('UPDATE users SET blocked = 0 WHERE id = ?').run(user.id);
  res.json({ ok: true, blocked: false });
});

// Kullanıcıyı admin'e yükselt
router.post('/users/:id/promote', (req, res) => {
  const user = db.prepare('SELECT id, role FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  db.prepare("UPDATE users SET role = 'admin', status = 'approved' WHERE id = ?").run(user.id);
  res.json({ ok: true, role: 'admin' });
});

// Yeni admin oluştur
router.post('/admins', (req, res) => {
  const { name, email, password } = req.body || {};
  if (!name || !email || !password) return res.status(400).json({ error: 'Ad, e-posta ve şifre zorunludur.' });
  if (String(password).length < 6) return res.status(400).json({ error: 'Şifre en az 6 karakter olmalıdır.' });
  if (db.prepare('SELECT id FROM users WHERE email = ?').get(email)) {
    return res.status(409).json({ error: 'Bu e-posta zaten kayıtlı.' });
  }
  const info = db
    .prepare("INSERT INTO users (role, name, email, password_hash, status) VALUES ('admin', ?, ?, ?, 'approved')")
    .run(name, email, bcrypt.hashSync(password, 10));
  res.status(201).json({ id: info.lastInsertRowid, role: 'admin', name, email });
});

// Kullanıcıyı sil (kendisi hariç)
router.delete('/users/:id', (req, res) => {
  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  if (user.id === req.user.id) return res.status(400).json({ error: 'Kendinizi silemezsiniz.' });
  db.prepare('DELETE FROM users WHERE id = ?').run(user.id);
  res.json({ ok: true });
});

// İçerik denetimi: bağış / istek / kitap silme
router.delete('/donations/:id', (req, res) => {
  const info = db.prepare('DELETE FROM donations WHERE id = ?').run(req.params.id);
  if (!info.changes) return res.status(404).json({ error: 'Bağış bulunamadı.' });
  res.json({ ok: true });
});

router.delete('/requests/:id', (req, res) => {
  const info = db.prepare('DELETE FROM requests WHERE id = ?').run(req.params.id);
  if (!info.changes) return res.status(404).json({ error: 'İstek bulunamadı.' });
  res.json({ ok: true });
});

router.delete('/books/:id', (req, res) => {
  const used = db.prepare('SELECT 1 FROM donations WHERE book_id = ? UNION SELECT 1 FROM requests WHERE book_id = ?')
    .get(req.params.id, req.params.id);
  if (used) return res.status(409).json({ error: 'Bu kitap bağış/isteklerde kullanıldığı için silinemez.' });
  const info = db.prepare('DELETE FROM books WHERE id = ?').run(req.params.id);
  if (!info.changes) return res.status(404).json({ error: 'Kitap bulunamadı.' });
  res.json({ ok: true });
});

module.exports = router;
