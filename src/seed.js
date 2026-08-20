'use strict';

const bcrypt = require('bcryptjs');
const db = require('./db');

// ADMIN_EMAIL ve ADMIN_PASSWORD ayarlıysa açılışta ilk admin'i oluşturur/garantiler.
function seedAdmin() {
  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;
  if (!email || !password) return null;

  const name = process.env.ADMIN_NAME || 'Yönetici';
  const existing = db.prepare('SELECT id, is_admin FROM users WHERE email = ?').get(email);
  if (existing) {
    if (!existing.is_admin) {
      db.prepare('UPDATE users SET is_admin = 1, blocked = 0 WHERE id = ?').run(existing.id);
    }
    return existing.id;
  }
  const info = db
    .prepare('INSERT INTO users (name, email, password_hash, is_admin) VALUES (?, ?, ?, 1)')
    .run(name, email, bcrypt.hashSync(password, 10));
  return info.lastInsertRowid;
}

module.exports = { seedAdmin };
