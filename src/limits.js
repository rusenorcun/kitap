'use strict';

const db = require('./db');

// Öğrenci bağış alma sınırları
const WEEKLY_LIMIT = 3;     // haftalık en fazla 3 kitap
const QUARTERLY_LIMIT = 10; // 3 ayda (90 gün) en fazla 10 kitap

// Bir öğrencinin "aldığı kitap" sayısı = onaylanmış talepler (claims) +
// karşılanmış istekler (fulfilled requests). Her ikisi de fiilen alınan kitaptır.
const countWeekly = db.prepare(`
  SELECT
    (SELECT COUNT(*) FROM claims
       WHERE student_id = @sid AND created_at >= datetime('now', '-7 days')) +
    (SELECT COUNT(*) FROM requests
       WHERE student_id = @sid AND status = 'fulfilled'
         AND fulfilled_at >= datetime('now', '-7 days')) AS total
`);

const countQuarterly = db.prepare(`
  SELECT
    (SELECT COUNT(*) FROM claims
       WHERE student_id = @sid AND created_at >= datetime('now', '-90 days')) +
    (SELECT COUNT(*) FROM requests
       WHERE student_id = @sid AND status = 'fulfilled'
         AND fulfilled_at >= datetime('now', '-90 days')) AS total
`);

// Öğrencinin güncel kotasını döndürür.
function getQuota(studentId) {
  const weeklyUsed = countWeekly.get({ sid: studentId }).total;
  const quarterlyUsed = countQuarterly.get({ sid: studentId }).total;
  return {
    weeklyUsed,
    weeklyLimit: WEEKLY_LIMIT,
    weeklyRemaining: Math.max(0, WEEKLY_LIMIT - weeklyUsed),
    quarterlyUsed,
    quarterlyLimit: QUARTERLY_LIMIT,
    quarterlyRemaining: Math.max(0, QUARTERLY_LIMIT - quarterlyUsed),
    canReceive: weeklyUsed < WEEKLY_LIMIT && quarterlyUsed < QUARTERLY_LIMIT,
  };
}

// Öğrenci bir kitap daha alabilir mi? Alamıyorsa sebep mesajı döner.
function checkCanReceive(studentId) {
  const q = getQuota(studentId);
  if (q.weeklyUsed >= WEEKLY_LIMIT) {
    return { ok: false, reason: `Haftalık ${WEEKLY_LIMIT} kitap sınırına ulaştınız.`, quota: q };
  }
  if (q.quarterlyUsed >= QUARTERLY_LIMIT) {
    return { ok: false, reason: `3 aylık ${QUARTERLY_LIMIT} kitap sınırına ulaştınız.`, quota: q };
  }
  return { ok: true, quota: q };
}

module.exports = { getQuota, checkCanReceive, WEEKLY_LIMIT, QUARTERLY_LIMIT };
