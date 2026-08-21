'use strict';

const jwt = require('jsonwebtoken');
const db = require('./db');

const JWT_SECRET = process.env.JWT_SECRET || 'kitap-dev-secret-change-me';
const TOKEN_TTL = '7d';

function signToken(user) {
  return jwt.sign({ id: user.id, name: user.name }, JWT_SECRET, { expiresIn: TOKEN_TTL });
}

// Öğrenci = belgesi onaylanmış üye
function isApprovedStudent(user) {
  return !!user && user.student_status === 'approved';
}

function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    isAdmin: !!user.is_admin,
    // Roller: herkes bağış yapabilir ve üye olarak alabilir
    canDonate: true,
    isStudent: isApprovedStudent(user),
    studentStatus: user.student_status,
    recipientTier: isApprovedStudent(user) ? 'student' : 'member',
    school_level: user.school_level || null,
    address: user.address || null,
    phone: user.phone || null,
    blocked: !!user.blocked,
  };
}

const USER_COLS =
  'id, name, email, is_admin, student_status, school_level, document_no, address, phone, blocked';

function authenticate(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (token) {
    try {
      const payload = jwt.verify(token, JWT_SECRET);
      const user = db.prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?`).get(payload.id);
      if (user) req.user = user;
    } catch (_err) {
      // geçersiz token: anonim
    }
  }
  next();
}

function requireAuth(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' });
  if (req.user.blocked) return res.status(403).json({ error: 'Hesabınız engellenmiş.' });
  next();
}

function requireAdmin(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' });
  if (req.user.blocked) return res.status(403).json({ error: 'Hesabınız engellenmiş.' });
  if (!req.user.is_admin) return res.status(403).json({ error: 'Bu işlem için yönetici yetkisi gerekir.' });
  next();
}

// Kitap almak/talep etmek/takas için: giriş + engelli değil + teslimat adresi tanımlı
function requireDeliverable(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Giriş yapmanız gerekiyor.' });
  if (req.user.blocked) return res.status(403).json({ error: 'Hesabınız engellenmiş.' });
  if (!req.user.address) {
    return res.status(403).json({
      error: 'Önce profilinizden teslimat adresi eklemelisiniz.',
      code: 'ADDRESS_REQUIRED',
    });
  }
  next();
}

module.exports = {
  signToken,
  publicUser,
  isApprovedStudent,
  authenticate,
  requireAuth,
  requireAdmin,
  requireDeliverable,
  JWT_SECRET,
};
