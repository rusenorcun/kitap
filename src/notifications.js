'use strict';

const db = require('./db');

// Bir kullanıcıya bildirim ekler. data nesnesi JSON olarak saklanır.
function notify(userId, type, message, data = null) {
  if (!userId) return;
  db.prepare('INSERT INTO notifications (user_id, type, message, data) VALUES (?, ?, ?, ?)')
    .run(userId, type, message, data ? JSON.stringify(data) : null);
}

function listForUser(userId, { unreadOnly = false, limit = 50 } = {}) {
  const rows = unreadOnly
    ? db.prepare('SELECT * FROM notifications WHERE user_id = ? AND is_read = 0 ORDER BY created_at DESC LIMIT ?').all(userId, limit)
    : db.prepare('SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?').all(userId, limit);
  return rows.map((n) => ({ ...n, is_read: !!n.is_read, data: n.data ? JSON.parse(n.data) : null }));
}

function unreadCount(userId) {
  return db.prepare('SELECT COUNT(*) AS n FROM notifications WHERE user_id = ? AND is_read = 0').get(userId).n;
}

function markRead(userId, id) {
  return db.prepare('UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?').run(id, userId).changes;
}

function markAllRead(userId) {
  return db.prepare('UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0').run(userId).changes;
}

module.exports = { notify, listForUser, unreadCount, markRead, markAllRead };
