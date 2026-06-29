'use strict';

const db = require('./db');

// Öğrenci bağış alma sınırları
const WEEKLY_LIMIT = 3;    // son 7 günde en fazla 3 kitap
const MONTHLY_LIMIT = 10;  // son 30 günde en fazla 10 kitap

// Bir öğrencinin "aldığı kitap" sayısı = onaylanmış talepler (claims) +
// karşılanmış istekler (fulfilled requests). Her ikisi de fiilen alınan kitaptır.
const countSince = db.prepare(`
  SELECT
    (SELECT COUNT(*) FROM claims
       WHERE student_id = @sid AND created_at >= datetime('now', @window)) +
    (SELECT COUNT(*) FROM requests
       WHERE student_id = @sid AND status IN ('fulfilled', 'shipped', 'delivered')
         AND fulfilled_at >= datetime('now', @window)) AS total
`);

function countWindow(studentId, window) {
  return countSince.get({ sid: studentId, window }).total;
}

// Öğrencinin güncel kotasını döndürür.
function getQuota(studentId) {
  const weeklyUsed = countWindow(studentId, '-7 days');
  const monthlyUsed = countWindow(studentId, '-30 days');
  return {
    weeklyUsed,
    weeklyLimit: WEEKLY_LIMIT,
    weeklyRemaining: Math.max(0, WEEKLY_LIMIT - weeklyUsed),
    monthlyUsed,
    monthlyLimit: MONTHLY_LIMIT,
    monthlyRemaining: Math.max(0, MONTHLY_LIMIT - monthlyUsed),
    canReceive: weeklyUsed < WEEKLY_LIMIT && monthlyUsed < MONTHLY_LIMIT,
  };
}

// Öğrenci bir kitap daha alabilir mi? Alamıyorsa sebep mesajı döner.
function checkCanReceive(studentId) {
  const q = getQuota(studentId);
  if (q.weeklyUsed >= WEEKLY_LIMIT) {
    return { ok: false, reason: `Haftalık ${WEEKLY_LIMIT} kitap sınırına ulaşıldı.`, quota: q };
  }
  if (q.monthlyUsed >= MONTHLY_LIMIT) {
    return { ok: false, reason: `30 günlük ${MONTHLY_LIMIT} kitap sınırına ulaşıldı.`, quota: q };
  }
  return { ok: true, quota: q };
}

module.exports = { getQuota, checkCanReceive, WEEKLY_LIMIT, MONTHLY_LIMIT };
