'use strict';

const bcrypt = require('bcryptjs');
const db = require('./db');

// ADMIN_EMAIL ve ADMIN_PASSWORD ayarlıysa açılışta ilk admin'i oluşturur/garantiler.
function seedAdmin() {
  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;
  if (!email || !password) return null;

  const name = process.env.ADMIN_NAME || 'Yönetici';
  const existing = db.prepare('SELECT id, role FROM users WHERE email = ?').get(email);
  if (existing) {
    // Mevcut kullanıcıyı admin'e yükselt (rol farklıysa)
    if (existing.role !== 'admin') {
      db.prepare("UPDATE users SET role = 'admin', status = 'approved', blocked = 0 WHERE id = ?").run(existing.id);
    }
    return existing.id;
  }
  const info = db
    .prepare("INSERT INTO users (role, name, email, password_hash, status) VALUES ('admin', ?, ?, ?, 'approved')")
    .run(name, email, bcrypt.hashSync(password, 10));
  return info.lastInsertRowid;
}

module.exports = { seedAdmin };
