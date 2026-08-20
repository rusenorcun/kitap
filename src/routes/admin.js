'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAdmin } = require('../auth');
const { notify } = require('../notifications');
const { isEmail, normalizeEmail, cleanStr } = require('../validate');

const router = express.Router();
const DOC_DIR = path.join(__dirname, '..', '..', 'uploads', 'documents');

router.use(requireAdmin);

const USER_COLUMNS = `id, name, email, is_admin, student_status, school_level, document_no,
                      address, phone, blocked, created_at`;

router.get('/stats', (req, res) => {
  const one = (sql) => db.prepare(sql).get().n;
  res.json({
    users: one('SELECT COUNT(*) AS n FROM users'),
    admins: one('SELECT COUNT(*) AS n FROM users WHERE is_admin = 1'),
    students: one("SELECT COUNT(*) AS n FROM users WHERE student_status = 'approved'"),
    pendingStudents: one("SELECT COUNT(*) AS n FROM users WHERE student_status = 'pending'"),
    members: one("SELECT COUNT(*) AS n FROM users WHERE is_admin = 0 AND student_status != 'approved'"),
    blocked: one('SELECT COUNT(*) AS n FROM users WHERE blocked = 1'),
    books: one('SELECT COUNT(*) AS n FROM books'),
    donations: one('SELECT COUNT(*) AS n FROM donations'),
    requests: one('SELECT COUNT(*) AS n FROM requests'),
    claims: one('SELECT COUNT(*) AS n FROM claims'),
    swapBooks: one('SELECT COUNT(*) AS n FROM swap_books'),
    swapOffers: one('SELECT COUNT(*) AS n FROM swap_offers'),
  });
});

// Kullanıcılar. Filtreler: ?student_status= ?blocked= ?admin=
router.get('/users', (req, res) => {
  const { student_status, blocked, admin } = req.query;
  const where = [];
  const params = {};
  if (student_status) { where.push('student_status = @student_status'); params.student_status = student_status; }
  if (blocked !== undefined) { where.push('blocked = @blocked'); params.blocked = (blocked === 'true' || blocked === '1') ? 1 : 0; }
  if (admin !== undefined) { where.push('is_admin = @admin'); params.admin = (admin === 'true' || admin === '1') ? 1 : 0; }
  const sql = `SELECT ${USER_COLUMNS} FROM users ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY created_at DESC`;
  res.json(db.prepare(sql).all(params));
});

router.get('/users/:id', (req, res) => {
  const user = db.prepare(`SELECT ${USER_COLUMNS} FROM users WHERE id = ?`).get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  res.json(user);
});

router.get('/users/:id/document', (req, res) => {
  const user = db.prepare('SELECT student_status, document_path FROM users WHERE id = ?').get(req.params.id);
  if (!user || !user.document_path) return res.status(404).json({ error: 'Belge bulunamadı.' });
  const full = path.join(DOC_DIR, path.basename(user.document_path));
  if (!fs.existsSync(full)) return res.status(404).json({ error: 'Belge dosyası bulunamadı.' });
  res.sendFile(full);
});

function setStudentStatus(req, res, status) {
  const user = db.prepare('SELECT id, student_status FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  if (user.student_status !== 'pending') {
    return res.status(409).json({ error: 'Bu kullanıcının incelenecek bir öğrenci belgesi yok.' });
  }
  db.prepare('UPDATE users SET student_status = ? WHERE id = ?').run(status, user.id);
  notify(
    user.id,
    status === 'approved' ? 'document_approved' : 'document_rejected',
    status === 'approved'
      ? 'Öğrenci belgeniz onaylandı. Artık öncelikli kitap alabilirsiniz.'
      : 'Öğrenci belgeniz reddedildi. Lütfen yöneticiyle iletişime geçin.'
  );
  res.json({ ok: true, student_status: status });
}

router.post('/users/:id/approve', (req, res) => setStudentStatus(req, res, 'approved'));
router.post('/users/:id/reject', (req, res) => setStudentStatus(req, res, 'rejected'));

router.post('/users/:id/block', (req, res) => {
  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(req.params.id);
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

router.post('/users/:id/promote', (req, res) => {
  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  db.prepare('UPDATE users SET is_admin = 1 WHERE id = ?').run(user.id);
  res.json({ ok: true, is_admin: true });
});

router.post('/admins', (req, res) => {
  const name = cleanStr(req.body && req.body.name, 120);
  const email = normalizeEmail(req.body && req.body.email);
  const password = req.body && req.body.password;
  if (!name || !email || !password) return res.status(400).json({ error: 'Ad, e-posta ve şifre zorunludur.' });
  if (!isEmail(email)) return res.status(400).json({ error: 'Geçerli bir e-posta adresi girin.' });
  if (String(password).length < 6) return res.status(400).json({ error: 'Şifre en az 6 karakter olmalıdır.' });
  if (db.prepare('SELECT id FROM users WHERE email = ?').get(email)) return res.status(409).json({ error: 'Bu e-posta zaten kayıtlı.' });
  const info = db.prepare('INSERT INTO users (name, email, password_hash, is_admin) VALUES (?, ?, ?, 1)')
    .run(name, email, bcrypt.hashSync(password, 10));
  res.status(201).json({ id: info.lastInsertRowid, name, email, is_admin: true });
});

router.delete('/users/:id', (req, res) => {
  const user = db.prepare('SELECT id FROM users WHERE id = ?').get(req.params.id);
  if (!user) return res.status(404).json({ error: 'Kullanıcı bulunamadı.' });
  if (user.id === req.user.id) return res.status(400).json({ error: 'Kendinizi silemezsiniz.' });
  db.prepare('DELETE FROM users WHERE id = ?').run(user.id);
  res.json({ ok: true });
});

// İçerik denetimi
router.get('/donations', (req, res) => {
  res.json(db.prepare(`
    SELECT d.*, u.name AS donor_name, b.title AS book_title, b.author AS book_author,
           (SELECT COUNT(*) FROM claims c WHERE c.donation_id = d.id) AS claimed
    FROM donations d JOIN users u ON u.id = d.donor_id JOIN books b ON b.id = d.book_id
    ORDER BY d.created_at DESC
  `).all());
});

router.get('/requests', (req, res) => {
  res.json(db.prepare(`
    SELECT r.*, u.name AS student_name, b.title AS book_title, b.author AS book_author, f.name AS fulfilled_by_name
    FROM requests r JOIN users u ON u.id = r.student_id JOIN books b ON b.id = r.book_id
    LEFT JOIN users f ON f.id = r.fulfilled_by ORDER BY r.created_at DESC
  `).all());
});

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

router.delete('/swap-books/:id', (req, res) => {
  const info = db.prepare('DELETE FROM swap_books WHERE id = ?').run(req.params.id);
  if (!info.changes) return res.status(404).json({ error: 'Takas kitabı bulunamadı.' });
  res.json({ ok: true });
});

router.delete('/books/:id', (req, res) => {
  const used = db.prepare(`SELECT 1 FROM donations WHERE book_id = ?1 UNION SELECT 1 FROM requests WHERE book_id = ?1
                           UNION SELECT 1 FROM swap_books WHERE book_id = ?1`).get(req.params.id);
  if (used) return res.status(409).json({ error: 'Bu kitap kullanımda olduğu için silinemez.' });
  const info = db.prepare('DELETE FROM books WHERE id = ?').run(req.params.id);
  if (!info.changes) return res.status(404).json({ error: 'Kitap bulunamadı.' });
  res.json({ ok: true });
});

module.exports = router;
