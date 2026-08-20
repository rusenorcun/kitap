'use strict';

const express = require('express');
const db = require('../db');
const { requireRole, requireApprovedStudent } = require('../auth');
const { checkCanReceive } = require('../limits');
const { notify } = require('../notifications');
const { cleanStr } = require('../validate');

const router = express.Router();

const VALID_TARGETS = ['ortaokul', 'lise', 'universite', 'hepsi'];
const VALID_SOURCES = ['purchase', 'own'];

// Bağış + kitap + kalan adet bilgisini birleştiren ortak sorgu
const DONATION_SELECT = `
  SELECT d.*, u.name AS donor_name,
         b.title AS book_title, b.author AS book_author,
         b.cover_url, b.purchase_link,
         (SELECT COUNT(*) FROM claims c WHERE c.donation_id = d.id) AS claimed,
         (d.quantity - (SELECT COUNT(*) FROM claims c WHERE c.donation_id = d.id)) AS remaining
  FROM donations d
  JOIN users u ON u.id = d.donor_id
  JOIN books b ON b.id = d.book_id
`;

// Bağışçı yeni kitap bağışı sunar (satın alarak ya da elindeki kopyayı göndererek)
router.post('/', requireRole('donor'), (req, res) => {
  const { book_id, description, quantity, target_level, source } = req.body || {};
  if (!book_id || !quantity) {
    return res.status(400).json({ error: 'Kitap (book_id) ve miktar zorunludur.' });
  }
  const book = db.prepare('SELECT id FROM books WHERE id = ?').get(book_id);
  if (!book) return res.status(404).json({ error: 'Seçilen kitap bulunamadı.' });

  const qty = Number(quantity);
  if (!Number.isInteger(qty) || qty < 1) {
    return res.status(400).json({ error: 'Miktar 1 veya daha büyük bir tam sayı olmalıdır.' });
  }
  const level = target_level || 'hepsi';
  if (!VALID_TARGETS.includes(level)) return res.status(400).json({ error: 'Hedef seviye geçersiz.' });
  const src = source || 'purchase';
  if (!VALID_SOURCES.includes(src)) {
    return res.status(400).json({ error: "Kaynak 'purchase' (satın al) veya 'own' (elimdeki) olmalıdır." });
  }

  const info = db
    .prepare(`INSERT INTO donations (donor_id, book_id, description, quantity, source, target_level)
              VALUES (?, ?, ?, ?, ?, ?)`)
    .run(req.user.id, book_id, description || null, qty, src, level);
  res.status(201).json(db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(info.lastInsertRowid));
});

// Açık bağışları listele. Filtreler: ?level= ?book_id= ?q= (kitap adı/yazar)
router.get('/', (req, res) => {
  const { level, book_id, q } = req.query;
  const where = ["d.status = 'open'"];
  const params = {};
  if (level && VALID_TARGETS.includes(level)) {
    // Seçilen seviyeye uygun bağışlar: tam eşleşme ya da 'hepsi'
    where.push("(d.target_level = @level OR d.target_level = 'hepsi')");
    params.level = level;
  }
  if (book_id) { where.push('d.book_id = @book_id'); params.book_id = Number(book_id); }
  if (q) { where.push('(b.title LIKE @like OR b.author LIKE @like)'); params.like = `%${q}%`; }

  const onlyAvailable = req.query.available !== 'false';
  const rows = db
    .prepare(`${DONATION_SELECT} WHERE ${where.join(' AND ')} ORDER BY d.created_at DESC`)
    .all(params);
  res.json(onlyAvailable ? rows.filter((d) => d.remaining > 0) : rows);
});

// Bağışçının kendi bağışları (alan öğrenciler + teslimat adresleriyle birlikte)
router.get('/mine', requireRole('donor'), (req, res) => {
  const rows = db.prepare(`${DONATION_SELECT} WHERE d.donor_id = ? ORDER BY d.created_at DESC`).all(req.user.id);
  // Eşleşen öğrencinin adresi yalnızca bağışı yapan bağışçıya gösterilir.
  const getClaimers = db.prepare(`
    SELECT c.id AS claim_id, c.status, c.shipped_at, c.delivered_at,
           u.name, u.school_level, u.address, u.phone, c.created_at
    FROM claims c JOIN users u ON u.id = c.student_id
    WHERE c.donation_id = ? ORDER BY c.created_at
  `);
  for (const d of rows) d.claimers = getClaimers.all(d.id);
  res.json(rows);
});

// Öğrenci bir bağıştan kitap alır (onaylı öğrenci + kota kontrolü)
router.post('/:id/claim', requireApprovedStudent, (req, res) => {
  const donation = db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(req.params.id);
  if (!donation || donation.status !== 'open') {
    return res.status(404).json({ error: 'Bağış bulunamadı.' });
  }
  if (donation.remaining <= 0) return res.status(409).json({ error: 'Bu bağışta kalan kitap kalmadı.' });
  if (donation.target_level !== 'hepsi' && donation.target_level !== req.user.school_level) {
    return res.status(403).json({ error: 'Bu bağış sizin okul seviyenize uygun değil.' });
  }
  if (db.prepare('SELECT id FROM claims WHERE donation_id = ? AND student_id = ?').get(donation.id, req.user.id)) {
    return res.status(409).json({ error: 'Bu bağıştan zaten bir kitap aldınız.' });
  }

  const check = checkCanReceive(req.user.id);
  if (!check.ok) return res.status(403).json({ error: check.reason, quota: check.quota });

  // Yarış durumlarına karşı işlemi transaction içinde, kalan adedi yeniden doğrulayarak yap
  const claim = db.transaction(() => {
    const count = db.prepare('SELECT COUNT(*) AS n FROM claims WHERE donation_id = ?').get(donation.id).n;
    if (count >= donation.quantity) throw new Error('SOLD_OUT');
    const info = db.prepare('INSERT INTO claims (donation_id, student_id) VALUES (?, ?)').run(donation.id, req.user.id);
    if (count + 1 >= donation.quantity) {
      db.prepare("UPDATE donations SET status = 'closed' WHERE id = ?").run(donation.id);
    }
    return info.lastInsertRowid;
  });

  try {
    const claimId = claim();
    notify(
      donation.donor_id,
      'donation_claimed',
      `${req.user.name}, "${donation.book_title}" bağışınızdan bir kitap aldı. Teslimat adresini panelinizden görebilirsiniz.`,
      { donation_id: donation.id, claim_id: claimId, student_id: req.user.id }
    );
    res.status(201).json({
      claim_id: claimId,
      donation: db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(donation.id),
      quota: checkCanReceive(req.user.id).quota,
    });
  } catch (err) {
    if (err.message === 'SOLD_OUT') return res.status(409).json({ error: 'Bu bağışta kalan kitap kalmadı.' });
    throw err;
  }
});

// Öğrencinin aldığı kitaplar
router.get('/claimed/mine', requireRole('student'), (req, res) => {
  const rows = db.prepare(`
    SELECT c.id AS claim_id, c.status, c.shipped_at, c.delivered_at, c.created_at,
           b.title AS book_title, b.author AS book_author, b.cover_url, u.name AS donor_name
    FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN books b ON b.id = d.book_id
    JOIN users u ON u.id = d.donor_id
    WHERE c.student_id = ? ORDER BY c.created_at DESC
  `).all(req.user.id);
  res.json(rows);
});

// Bağışçı kargoya verdi olarak işaretler
router.post('/claims/:claimId/ship', requireRole('donor'), (req, res) => {
  const row = db.prepare(`
    SELECT c.*, b.title AS book_title FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN books b ON b.id = d.book_id
    WHERE c.id = ? AND d.donor_id = ?
  `).get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Teslimat kaydı bulunamadı.' });
  if (row.status !== 'matched') return res.status(409).json({ error: 'Bu kayıt zaten kargolanmış.' });
  db.prepare("UPDATE claims SET status = 'shipped', shipped_at = datetime('now') WHERE id = ?").run(row.id);
  notify(row.student_id, 'claim_shipped', `"${row.book_title}" kitabınız kargoya verildi.`,
    { claim_id: row.id, donation_id: row.donation_id });
  res.json({ ok: true, status: 'shipped' });
});

// Öğrenci teslim aldı olarak işaretler
router.post('/claims/:claimId/deliver', requireRole('student'), (req, res) => {
  const row = db.prepare(`
    SELECT c.*, d.donor_id, b.title AS book_title FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN books b ON b.id = d.book_id
    WHERE c.id = ? AND c.student_id = ?
  `).get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Teslimat kaydı bulunamadı.' });
  if (row.status === 'delivered') return res.status(409).json({ error: 'Bu kayıt zaten teslim alınmış.' });
  db.prepare("UPDATE claims SET status = 'delivered', delivered_at = datetime('now') WHERE id = ?").run(row.id);
  notify(row.donor_id, 'claim_delivered', `${req.user.name}, "${row.book_title}" kitabını teslim aldı.`,
    { claim_id: row.id, donation_id: row.donation_id });
  res.json({ ok: true, status: 'delivered' });
});

// Öğrenci teslim aldığı kitap için bağışçıya teşekkür notu gönderir
router.post('/claims/:claimId/thank', requireRole('student'), (req, res) => {
  const row = db.prepare(`
    SELECT c.*, d.donor_id, b.title AS book_title FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN books b ON b.id = d.book_id
    WHERE c.id = ? AND c.student_id = ?
  `).get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Teslimat kaydı bulunamadı.' });
  if (row.status !== 'delivered') {
    return res.status(409).json({ error: 'Teşekkür notu yalnızca teslim alınan kitaplar için gönderilebilir.' });
  }
  const message = cleanStr(req.body && req.body.message, 300);
  const suffix = message ? ` Notu: "${message}"` : '';
  notify(row.donor_id, 'thank_you',
    `${req.user.name}, "${row.book_title}" bağışınız için teşekkür etti.${suffix}`,
    { donation_id: row.donation_id });
  res.status(201).json({ ok: true });
});

// Öğrenci aldığı kitabı iptal eder (kargolanmadan önce). Adet geri açılır, kota serbest kalır.
router.delete('/claims/:claimId', requireRole('student'), (req, res) => {
  const row = db.prepare(`
    SELECT c.*, d.donor_id, b.title AS book_title FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN books b ON b.id = d.book_id
    WHERE c.id = ? AND c.student_id = ?
  `).get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Talep bulunamadı.' });
  if (row.status !== 'matched') {
    return res.status(409).json({ error: 'Kargolanmış veya teslim edilmiş bir talep iptal edilemez.' });
  }
  db.transaction(() => {
    db.prepare('DELETE FROM claims WHERE id = ?').run(row.id);
    // Bağış tükenmiş olarak kapanmışsa tekrar açılır
    db.prepare("UPDATE donations SET status = 'open' WHERE id = ? AND status = 'closed'").run(row.donation_id);
  })();
  notify(row.donor_id, 'claim_cancelled', `${req.user.name}, "${row.book_title}" bağışınızdan aldığı kitabı iptal etti.`,
    { donation_id: row.donation_id });
  res.json({ ok: true });
});

// Bağışçı kendi bağışını kapatır (yeni talep alınmaz)
router.post('/:id/close', requireRole('donor'), (req, res) => {
  const donation = db.prepare('SELECT * FROM donations WHERE id = ? AND donor_id = ?').get(req.params.id, req.user.id);
  if (!donation) return res.status(404).json({ error: 'Bağış bulunamadı.' });
  db.prepare("UPDATE donations SET status = 'closed' WHERE id = ?").run(donation.id);
  res.json({ ok: true, status: 'closed' });
});

// Bağışçı kendi bağışını siler (henüz talep alınmamışsa)
router.delete('/:id', requireRole('donor'), (req, res) => {
  const donation = db.prepare('SELECT * FROM donations WHERE id = ? AND donor_id = ?').get(req.params.id, req.user.id);
  if (!donation) return res.status(404).json({ error: 'Bağış bulunamadı.' });
  const claims = db.prepare('SELECT COUNT(*) AS n FROM claims WHERE donation_id = ?').get(donation.id).n;
  if (claims > 0) {
    return res.status(409).json({ error: 'Talep alınmış bir bağış silinemez; bunun yerine kapatabilirsiniz.' });
  }
  db.prepare('DELETE FROM donations WHERE id = ?').run(donation.id);
  res.json({ ok: true });
});

module.exports = router;
