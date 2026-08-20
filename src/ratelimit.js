'use strict';

// Basit bellek-içi kayan pencere oran sınırlayıcı (harici bağımlılık yok).
// Özellikle başarısız giriş denemelerini sınırlayarak brute-force'u yavaşlatır.

function createRateLimiter({ windowMs = 15 * 60 * 1000, max = 8 } = {}) {
  const hits = new Map(); // key -> [timestamps]

  function check(key) {
    const now = Date.now();
    const arr = (hits.get(key) || []).filter((t) => now - t < windowMs);
    hits.set(key, arr);
    return { limited: arr.length >= max, remaining: Math.max(0, max - arr.length) };
  }

  function record(key) {
    const now = Date.now();
    const arr = (hits.get(key) || []).filter((t) => now - t < windowMs);
    arr.push(now);
    hits.set(key, arr);
  }

  function reset(key) {
    if (key) hits.delete(key);
    else hits.clear();
  }

  return { check, record, reset, windowMs, max };
}

module.exports = { createRateLimiter };
