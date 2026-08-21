'use strict';

// Alışveriş linkinden kitap üst verisini (başlık/kapak) çeken yardımcı.
// parseOgTags saf bir fonksiyondur ve birim testlerinde doğrudan kullanılır.

function decodeEntities(s) {
  if (!s) return s;
  return s
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&#x27;/gi, "'")
    .replace(/&apos;/g, "'")
    .replace(/&nbsp;/g, ' ');
}

// <meta property="og:title" content="..."> (içerik ve özellik sırası değişebilir)
function metaContent(html, prop) {
  const escaped = prop.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const patterns = [
    new RegExp(`<meta[^>]+(?:property|name)=["']${escaped}["'][^>]*?content=["']([^"']*)["']`, 'i'),
    new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]*?(?:property|name)=["']${escaped}["']`, 'i'),
  ];
  for (const re of patterns) {
    const m = html.match(re);
    if (m) return decodeEntities(m[1].trim());
  }
  return null;
}

function parseOgTags(html) {
  const titleTag = html.match(/<title[^>]*>([^<]*)<\/title>/i);
  return {
    title: metaContent(html, 'og:title') || (titleTag ? decodeEntities(titleTag[1].trim()) : null),
    image: metaContent(html, 'og:image') || metaContent(html, 'og:image:secure_url'),
    description: metaContent(html, 'og:description') || metaContent(html, 'description'),
    // Yazar standart OG'de yoktur; bazı sitelerde bulunabilir.
    author:
      metaContent(html, 'book:author') ||
      metaContent(html, 'author') ||
      metaContent(html, 'og:author') ||
      null,
  };
}

// HTTPS_PROXY ayarlıysa undici proxy dispatcher'ını bir kez yapılandır.
let proxyConfigured = false;
function maybeConfigureProxy() {
  if (proxyConfigured) return;
  proxyConfigured = true;
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
  if (!proxy) return;
  try {
    const { ProxyAgent, setGlobalDispatcher } = require('undici');
    setGlobalDispatcher(new ProxyAgent(proxy));
  } catch (_err) {
    // undici erişilebilir değilse sessizce doğrudan bağlantıyı dene
  }
}

async function fetchMetadata(url, { timeoutMs = 8000 } = {}) {
  if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) {
    throw new Error('Geçerli bir http(s) bağlantısı girin.');
  }
  maybeConfigureProxy();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      redirect: 'follow',
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; KitapBot/1.0)',
        Accept: 'text/html,application/xhtml+xml',
      },
    });
    if (!res.ok) throw new Error(`Bağlantı getirilemedi (HTTP ${res.status}).`);
    const html = await res.text();
    return parseOgTags(html);
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('Bağlantı zaman aşımına uğradı.');
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

module.exports = { parseOgTags, fetchMetadata, decodeEntities };
