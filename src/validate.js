'use strict';

// Basit ama yeterli girdi doğrulama yardımcıları

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isEmail(value) {
  return typeof value === 'string' && value.length <= 254 && EMAIL_RE.test(value);
}

function normalizeEmail(value) {
  return typeof value === 'string' ? value.trim().toLowerCase() : value;
}

// Boşlukları temizler, çok uzunsa keser; boşsa null döner.
function cleanStr(value, maxLen = 500) {
  if (value == null) return null;
  const s = String(value).trim();
  if (!s) return null;
  return s.slice(0, maxLen);
}

module.exports = { isEmail, normalizeEmail, cleanStr, EMAIL_RE };
