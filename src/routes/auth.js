'use strict';

const express = require('express');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { signToken, publicUser } = require('../auth');
const { isEmail, normalizeEmail, cleanStr } = require('../validate');
const { createRateLimiter } = require('../ratelimit');

const router = express.Router();

const loginLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, max: 8 });

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
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const ok = /^(image\/(png|jpe?g|webp)|application\/pdf)$/.test(file.mimetype);
    cb(ok ? null : new Error('Belge yalnızca PDF veya görsel (PNG/JPG/WEBP) olabilir.'), ok);
  },
});

const VALID_LEVELS = ['ortaokul', 'lise', 'universite'];

// Tek kayıt ucu: her kayıt bir ÜYE oluşturur (bağış yapabilir + üye olarak alabilir).
// Belge + okul seviyesi + belge no verilirse öğrenci doğrulaması 'pending' başlar.
router.post('/register', upload.single('document'), (req, res) => {
  const body = req.body || {};
  const name = cleanStr(body.name, 120);
  const email = normalizeEmail(body.email);
  const password = body.password;
  const address = cleanStr(body.address, 500);
  const phone = cleanStr(body.phone, 40);
  const wantsStudent = !!(req.file || body.document_no || body.school_level);
  const school_level = body.school_level;
  const document_no = cleanStr(body.document_no, 100);

  const cleanup = () => { if (req.file) fs.promises.unlink(req.file.path).catch(() => {}); };
  const fail = (code, msg) => { cleanup(); return res.status(code).json({ error: msg }); };

  if (!name || !email || !password) return fail(400, 'Ad, e-posta ve şifre zorunludur.');
  if (!isEmail(email)) return fail(400, 'Geçerli bir e-posta adresi girin.');
  if (String(password).length < 6) return fail(400, 'Şifre en az 6 karakter olmalıdır.');

  if (wantsStudent) {
    if (!req.file) return res.status(400).json({ error: 'Öğrenci doğrulaması için belge (PDF/görsel) gerekir.' });
    if (!VALID_LEVELS.includes(school_level)) return fail(400, 'Okul seviyesi ortaokul, lise veya universite olmalıdır.');
    if (!document_no) return fail(400, 'Öğrenci belge numarası zorunludur.');
    if (!address) return fail(400, 'Öğrenci doğrulaması için teslimat adresi zorunludur.');
    if (db.prepare('SELECT id FROM users WHERE document_no = ?').get(document_no)) {
      return fail(409, 'Bu öğrenci belgesi numarası ile zaten bir kayıt mevcut.');
    }
  }
  if (db.prepare('SELECT id FROM users WHERE email = ?').get(email)) {
    return fail(409, 'Bu e-posta zaten kayıtlı.');
  }

  const hash = bcrypt.hashSync(password, 10);
  try {
    const info = db.prepare(`
      INSERT INTO users (name, email, password_hash, student_status, school_level, document_no, document_path, address, phone)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      name, email, hash,
      wantsStudent ? 'pending' : 'none',
      wantsStudent ? school_level : null,
      wantsStudent ? document_no : null,
      wantsStudent ? path.basename(req.file.path) : null,
      address || null,
      phone || null
    );
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

router.post('/login', (req, res) => {
  const email = normalizeEmail(req.body && req.body.email);
  const password = req.body && req.body.password;
  if (!email || !password) return res.status(400).json({ error: 'E-posta ve şifre zorunludur.' });

  const key = `${req.ip}:${email}`;
  if (loginLimiter.check(key).limited) {
    return res.status(429).json({ error: 'Çok fazla başarısız giriş denemesi. Lütfen daha sonra tekrar deneyin.' });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user || !bcrypt.compareSync(password, user.password_hash)) {
    loginLimiter.record(key);
    return res.status(401).json({ error: 'E-posta veya şifre hatalı.' });
  }
  if (user.blocked) return res.status(403).json({ error: 'Hesabınız engellenmiş.' });
  loginLimiter.reset(key);
  res.json({ token: signToken(user), user: publicUser(user) });
});

module.exports = router;
