'use strict';

const jwt = require('jsonwebtoken');
const db = require('./db');

const JWT_SECRET = process.env.JWT_SECRET || 'kitap-dev-secret-change-me';
const TOKEN_TTL = '7d';

function signToken(user) {
  return jwt.sign(
    { id: user.id, role: user.role, name: user.name },
    JWT_SECRET,
    { expiresIn: TOKEN_TTL }
  );
}

// Token varsa req.user'ı doldurur; yoksa sessizce devam eder.
function authenticate(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (token) {
    try {
      const payload = jwt.verify(token, JWT_SECRET);
      const user = db.prepare('SELECT id, role, name, email, school_level FROM users WHERE id = ?').get(payload.id);
      if (user) req.user = user;
    } catch (_err) {
      // geçersiz token: kullanıcıyı anonim kabul et
    }
  }
  next();
}

function requireAuth(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' });
  next();
}

function requireRole(role) {
  return (req, res, next) => {
    if (!req.user) return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' });
    if (req.user.role !== role) {
      return res.status(403).json({ error: 'Bu işlem için yetkiniz yok.' });
    }
    next();
  };
}

module.exports = { signToken, authenticate, requireAuth, requireRole, JWT_SECRET };
