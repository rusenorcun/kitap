'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { signToken, publicUser } = require('../auth');

const router = express.Router();

// Öğrenci belgesi yüklemeleri için depolama
const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads', 'documents');
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    cb(null, `${Date.now()}-${Math.round(Math.random() * 1e9)}-${safe}`);
  },
});
const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5 MB
  fileFilter: (req, file, cb) => {
    const ok = /^(image\/(png|jpe?g|webp)|application\/pdf)$/.test(file.mimetype);
    cb(ok ? null : new Error('Belge yalnızca PDF veya görsel (PNG/JPG/WEBP) olabilir.'), ok);
  },
});

const VALID_LEVELS = ['ortaokul', 'lise', 'universite'];

// Bağışçı kaydı (sınır yok, anında onaylı)
router.post('/register/donor', (req, res) => {
  const { name, email, password } = req.body || {};
  if (!name || !email || !password) {
    return res.status(400).json({ error: 'Ad, e-posta ve şifre zorunludur.' });
  }
  if (String(password).length < 6) {
    return res.status(400).json({ error: 'Şifre en az 6 karakter olmalıdır.' });
  }
  if (db.prepare('SELECT id FROM users WHERE email = ?').get(email)) {
    return res.status(409).json({ error: 'Bu e-posta zaten kayıtlı.' });
  }

  const hash = bcrypt.hashSync(password, 10);
  const info = db
    .prepare("INSERT INTO users (role, name, email, password_hash, status) VALUES ('donor', ?, ?, ?, 'approved')")
    .run(name, email, hash);
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(info.lastInsertRowid);
  res.status(201).json({ token: signToken(user), user: publicUser(user) });
});

// Öğrenci kaydı — Öğrenci belgesi (belge no + dosya) ve teslimat adresi zorunludur.
// Kayıt 'pending' durumunda açılır; admin onayına kadar işlem yapılamaz.
router.post('/register/student', upload.single('document'), (req, res) => {
  const { name, email, password, school_level, document_no, address, phone } = req.body || {};

  const cleanup = () => {
    if (req.file) fs.promises.unlink(req.file.path).catch(() => {});
  };

  if (!name || !email || !password || !school_level || !document_no || !address) {
    cleanup();
    return res.status(400).json({
      error: 'Ad, e-posta, şifre, okul seviyesi, öğrenci belge numarası ve teslimat adresi zorunludur.',
    });
  }
  if (!VALID_LEVELS.includes(school_level)) {
    cleanup();
    return res.status(400).json({ error: 'Okul seviyesi ortaokul, lise veya universite olmalıdır.' });
  }
  if (String(password).length < 6) {
    cleanup();
    return res.status(400).json({ error: 'Şifre en az 6 karakter olmalıdır.' });
  }
  if (!req.file) {
    return res.status(400).json({ error: 'Öğrenci belgesi (PDF/görsel) yüklenmesi zorunludur.' });
  }

  if (db.prepare('SELECT id FROM users WHERE email = ?').get(email)) {
    cleanup();
    return res.status(409).json({ error: 'Bu e-posta zaten kayıtlı.' });
  }
  // Tek belge ile iki kayıt açılamaz.
  if (db.prepare('SELECT id FROM users WHERE document_no = ?').get(document_no)) {
    cleanup();
    return res.status(409).json({ error: 'Bu öğrenci belgesi numarası ile zaten bir kayıt mevcut.' });
  }

  const hash = bcrypt.hashSync(password, 10);
  try {
    const info = db
      .prepare(`INSERT INTO users (role, name, email, password_hash, status, school_level, document_no, document_path, address, phone)
                VALUES ('student', ?, ?, ?, 'pending', ?, ?, ?, ?, ?)`)
      .run(name, email, hash, school_level, document_no, path.basename(req.file.path), address, phone || null);
    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(info.lastInsertRowid);
    res.status(201).json({ token: signToken(user), user: publicUser(user) });
  } catch (err) {
    cleanup();
    if (err.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: 'E-posta veya belge numarası zaten kayıtlı.' });
    }
    throw err;
  }
});

// Giriş
router.post('/login', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) return res.status(400).json({ error: 'E-posta ve şifre zorunludur.' });

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user || !bcrypt.compareSync(password, user.password_hash)) {
    return res.status(401).json({ error: 'E-posta veya şifre hatalı.' });
  }
  if (user.blocked) return res.status(403).json({ error: 'Hesabınız engellenmiş.' });
  res.json({ token: signToken(user), user: publicUser(user) });
});

module.exports = router;
