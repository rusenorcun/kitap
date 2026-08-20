'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAuth, publicUser } = require('../auth');
const { getQuota } = require('../limits');
const { cleanStr } = require('../validate');
const notifications = require('../notifications');

const router = express.Router();

const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads', 'documents');
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });
const upload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => cb(null, UPLOAD_DIR),
    filename: (req, file, cb) => {
      const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
      cb(null, `${Date.now()}-${Math.round(Math.random() * 1e9)}-${safe}`);
    },
  }),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const ok = /^(image\/(png|jpe?g|webp)|application\/pdf)$/.test(file.mimetype);
    cb(ok ? null : new Error('Belge yalnızca PDF veya görsel (PNG/JPG/WEBP) olabilir.'), ok);
  },
});
const VALID_LEVELS = ['ortaokul', 'lise', 'universite'];

router.get('/', requireAuth, (req, res) => {
  res.json(publicUser(db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id)));
});

// Profil güncelle (ad, adres, telefon, şifre)
router.patch('/', requireAuth, (req, res) => {
  const body = req.body || {};
  const updates = {};

  const name = cleanStr(body.name, 120);
  if (name) updates.name = name;
  if ('address' in body) {
    const address = cleanStr(body.address, 500);
    if (!address) return res.status(400).json({ error: 'Adres boş olamaz.' });
    updates.address = address;
  }
  if ('phone' in body) updates.phone = cleanStr(body.phone, 40);

  if (body.new_password) {
    if (String(body.new_password).length < 6) {
      return res.status(400).json({ error: 'Yeni şifre en az 6 karakter olmalıdır.' });
    }
    const user = db.prepare('SELECT password_hash FROM users WHERE id = ?').get(req.user.id);
    if (!body.current_password || !bcrypt.compareSync(body.current_password, user.password_hash)) {
      return res.status(403).json({ error: 'Mevcut şifre hatalı.' });
    }
    updates.password_hash = bcrypt.hashSync(body.new_password, 10);
  }

  if (!Object.keys(updates).length) return res.status(400).json({ error: 'Güncellenecek alan yok.' });
  const setSql = Object.keys(updates).map((k) => `${k} = @${k}`).join(', ');
  db.prepare(`UPDATE users SET ${setSql} WHERE id = @id`).run({ ...updates, id: req.user.id });
  res.json(publicUser(db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id)));
});

// Öğrenci doğrulaması başlat (üyeyken öğrenciye yükselme). Admin onayına gider.
router.post('/verify-student', upload.single('document'), (req, res) => {
  const body = req.body || {};
  const school_level = body.school_level;
  const document_no = cleanStr(body.document_no, 100);
  const cleanup = () => { if (req.file) fs.promises.unlink(req.file.path).catch(() => {}); };

  if (!req.user) { cleanup(); return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' }); }
  if (req.user.blocked) { cleanup(); return res.status(403).json({ error: 'Hesabınız engellenmiş.' }); }

  const me = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  if (me.student_status === 'approved') { cleanup(); return res.status(409).json({ error: 'Zaten onaylı bir öğrencisiniz.' }); }
  if (me.student_status === 'pending') { cleanup(); return res.status(409).json({ error: 'Belgeniz zaten incelemede.' }); }
  if (!req.file) return res.status(400).json({ error: 'Öğrenci belgesi (PDF/görsel) zorunludur.' });
  if (!VALID_LEVELS.includes(school_level)) { cleanup(); return res.status(400).json({ error: 'Okul seviyesi geçersiz.' }); }
  if (!document_no) { cleanup(); return res.status(400).json({ error: 'Öğrenci belge numarası zorunludur.' }); }
  if (!me.address) { cleanup(); return res.status(400).json({ error: 'Önce teslimat adresi eklemelisiniz.' }); }
  const dupe = db.prepare('SELECT id FROM users WHERE document_no = ? AND id != ?').get(document_no, me.id);
  if (dupe) { cleanup(); return res.status(409).json({ error: 'Bu belge numarası başka bir kayıtta kullanılıyor.' }); }

  try {
    db.prepare(`UPDATE users SET student_status = 'pending', school_level = ?, document_no = ?, document_path = ? WHERE id = ?`)
      .run(school_level, document_no, path.basename(req.file.path), me.id);
  } catch (err) {
    cleanup();
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE') return res.status(409).json({ error: 'Bu belge numarası zaten kayıtlı.' });
    throw err;
  }
  res.status(202).json(publicUser(db.prepare('SELECT * FROM users WHERE id = ?').get(me.id)));
});

// Kota (üye veya öğrenci)
router.get('/quota', requireAuth, (req, res) => {
  res.json(getQuota(req.user));
});

// Bildirimler
router.get('/notifications', requireAuth, (req, res) => {
  res.json(notifications.listForUser(req.user.id, { unreadOnly: req.query.unread === 'true' }));
});
router.get('/notifications/unread-count', requireAuth, (req, res) => {
  res.json({ count: notifications.unreadCount(req.user.id) });
});
router.post('/notifications/:id/read', requireAuth, (req, res) => {
  const changed = notifications.markRead(req.user.id, req.params.id);
  if (!changed) return res.status(404).json({ error: 'Bildirim bulunamadı.' });
  res.json({ ok: true });
});
router.post('/notifications/read-all', requireAuth, (req, res) => {
  res.json({ ok: true, updated: notifications.markAllRead(req.user.id) });
});

module.exports = router;
