'use strict';

const db = require('./db');
const { isApprovedStudent } = require('./auth');

// Öğrenci önceliği: yeni bağış ilk 48 saat yalnızca öğrencilere açık
const PRIORITY_WINDOW_HOURS = 48;

// Alıcı tier'ına göre bağış alma sınırları
const LIMITS = {
  student: { weekly: 3, monthly: 10 },
  member: { weekly: 1, monthly: 3 },
};

function tierOf(user) {
  return isApprovedStudent(user) ? 'student' : 'member';
}

const countSince = db.prepare(`
  SELECT
    (SELECT COUNT(*) FROM claims
       WHERE student_id = @sid AND created_at >= datetime('now', @window)) +
    (SELECT COUNT(*) FROM requests
       WHERE student_id = @sid AND status IN ('fulfilled', 'shipped', 'delivered')
         AND fulfilled_at >= datetime('now', @window)) AS total
`);

function countWindow(userId, window) {
  return countSince.get({ sid: userId, window }).total;
}

// user: authenticate ile yüklenmiş kullanıcı satırı (student_status içerir)
function getQuota(user) {
  const tier = tierOf(user);
  const limit = LIMITS[tier];
  const weeklyUsed = countWindow(user.id, '-7 days');
  const monthlyUsed = countWindow(user.id, '-30 days');
  return {
    tier,
    weeklyUsed,
    weeklyLimit: limit.weekly,
    weeklyRemaining: Math.max(0, limit.weekly - weeklyUsed),
    monthlyUsed,
    monthlyLimit: limit.monthly,
    monthlyRemaining: Math.max(0, limit.monthly - monthlyUsed),
    canReceive: weeklyUsed < limit.weekly && monthlyUsed < limit.monthly,
  };
}

function checkCanReceive(user) {
  const q = getQuota(user);
  if (q.weeklyUsed >= q.weeklyLimit) {
    return { ok: false, reason: `Haftalık ${q.weeklyLimit} kitap sınırına ulaşıldı.`, quota: q };
  }
  if (q.monthlyUsed >= q.monthlyLimit) {
    return { ok: false, reason: `30 günlük ${q.monthlyLimit} kitap sınırına ulaşıldı.`, quota: q };
  }
  return { ok: true, quota: q };
}

module.exports = { getQuota, checkCanReceive, tierOf, LIMITS, PRIORITY_WINDOW_HOURS };
