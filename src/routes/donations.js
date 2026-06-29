'use strict';

const express = require('express');
const db = require('../db');
const { requireRole, requireApprovedStudent } = require('../auth');
const { checkCanReceive } = require('../limits');

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

// Açık bağışları listele (kitap alınabilecek bağışlar)
router.get('/', (req, res) => {
  const onlyAvailable = req.query.available !== 'false';
  const rows = db.prepare(`${DONATION_SELECT} WHERE d.status = 'open' ORDER BY d.created_at DESC`).all();
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
    SELECT c.* FROM claims c JOIN donations d ON d.id = c.donation_id
    WHERE c.id = ? AND d.donor_id = ?
  `).get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Teslimat kaydı bulunamadı.' });
  if (row.status !== 'matched') return res.status(409).json({ error: 'Bu kayıt zaten kargolanmış.' });
  db.prepare("UPDATE claims SET status = 'shipped', shipped_at = datetime('now') WHERE id = ?").run(row.id);
  res.json({ ok: true, status: 'shipped' });
});

// Öğrenci teslim aldı olarak işaretler
router.post('/claims/:claimId/deliver', requireRole('student'), (req, res) => {
  const row = db.prepare('SELECT * FROM claims WHERE id = ? AND student_id = ?').get(req.params.claimId, req.user.id);
  if (!row) return res.status(404).json({ error: 'Teslimat kaydı bulunamadı.' });
  if (row.status === 'delivered') return res.status(409).json({ error: 'Bu kayıt zaten teslim alınmış.' });
  db.prepare("UPDATE claims SET status = 'delivered', delivered_at = datetime('now') WHERE id = ?").run(row.id);
  res.json({ ok: true, status: 'delivered' });
});

module.exports = router;
