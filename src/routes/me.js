'use strict';

const express = require('express');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAuth, requireRole, publicUser } = require('../auth');
const { getQuota } = require('../limits');
const { cleanStr } = require('../validate');
const notifications = require('../notifications');

const router = express.Router();

// Profil görüntüle
router.get('/', requireAuth, (req, res) => {
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  res.json(publicUser(user));
});

// Profil güncelle (ad, adres, telefon, şifre). Öğrenci adresini buradan değiştirir.
router.patch('/', requireAuth, (req, res) => {
  const body = req.body || {};
  const updates = {};

  const name = cleanStr(body.name, 120);
  if (name) updates.name = name;

  // Adres/telefon yalnızca öğrenciler için anlamlı
  if (req.user.role === 'student') {
    if ('address' in body) {
      const address = cleanStr(body.address, 500);
      if (!address) return res.status(400).json({ error: 'Adres boş olamaz.' });
      updates.address = address;
    }
    if ('phone' in body) updates.phone = cleanStr(body.phone, 40);
  }

  // Şifre değişimi: mevcut şifre doğrulanır
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

  if (!Object.keys(updates).length) {
    return res.status(400).json({ error: 'Güncellenecek alan yok.' });
  }
  const setSql = Object.keys(updates).map((k) => `${k} = @${k}`).join(', ');
  db.prepare(`UPDATE users SET ${setSql} WHERE id = @id`).run({ ...updates, id: req.user.id });
  res.json(publicUser(db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id)));
});

// Öğrencinin kalan bağış hakkı (kota)
router.get('/quota', requireRole('student'), (req, res) => {
  res.json(getQuota(req.user.id));
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
