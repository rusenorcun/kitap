'use strict';

const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../auth');
const { checkCanReceive } = require('../limits');

const router = express.Router();

const VALID_TARGETS = ['ortaokul', 'lise', 'universite', 'hepsi'];

// Bağış + kalan adet bilgisini hesaplayan ortak sorgu parçası
const DONATION_SELECT = `
  SELECT d.*, u.name AS donor_name,
         (SELECT COUNT(*) FROM claims c WHERE c.donation_id = d.id) AS claimed,
         (d.quantity - (SELECT COUNT(*) FROM claims c WHERE c.donation_id = d.id)) AS remaining
  FROM donations d
  JOIN users u ON u.id = d.donor_id
`;

// Bağışçı yeni kitap bağışı sunar
router.post('/', requireRole('donor'), (req, res) => {
  const { book_title, book_author, description, quantity, target_level } = req.body || {};
  if (!book_title || !quantity) {
    return res.status(400).json({ error: 'Kitap adı ve miktar zorunludur.' });
  }
  const qty = Number(quantity);
  if (!Number.isInteger(qty) || qty < 1) {
    return res.status(400).json({ error: 'Miktar 1 veya daha büyük bir tam sayı olmalıdır.' });
  }
  const level = target_level || 'hepsi';
  if (!VALID_TARGETS.includes(level)) {
    return res.status(400).json({ error: 'Hedef seviye geçersiz.' });
  }

  const info = db
    .prepare(`INSERT INTO donations (donor_id, book_title, book_author, description, quantity, target_level)
              VALUES (?, ?, ?, ?, ?, ?)`)
    .run(req.user.id, book_title, book_author || null, description || null, qty, level);
  const donation = db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(info.lastInsertRowid);
  res.status(201).json(donation);
});

// Açık bağışları listele (kitap alınabilecek bağışlar)
router.get('/', (req, res) => {
  const onlyAvailable = req.query.available !== 'false';
  const rows = db.prepare(`${DONATION_SELECT} WHERE d.status = 'open' ORDER BY d.created_at DESC`).all();
  const list = onlyAvailable ? rows.filter((d) => d.remaining > 0) : rows;
  res.json(list);
});

// Bağışçının kendi bağışları (kimler aldı bilgisiyle)
router.get('/mine', requireRole('donor'), (req, res) => {
  const rows = db
    .prepare(`${DONATION_SELECT} WHERE d.donor_id = ? ORDER BY d.created_at DESC`)
    .all(req.user.id);
  const getClaimers = db.prepare(`
    SELECT u.name, u.school_level, c.created_at
    FROM claims c JOIN users u ON u.id = c.student_id
    WHERE c.donation_id = ? ORDER BY c.created_at
  `);
  for (const d of rows) d.claimers = getClaimers.all(d.id);
  res.json(rows);
});

// Öğrenci bir bağıştan kitap alır
router.post('/:id/claim', requireRole('student'), (req, res) => {
  const donation = db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(req.params.id);
  if (!donation || donation.status !== 'open') {
    return res.status(404).json({ error: 'Bağış bulunamadı.' });
  }
  if (donation.remaining <= 0) {
    return res.status(409).json({ error: 'Bu bağışta kalan kitap kalmadı.' });
  }
  if (donation.target_level !== 'hepsi' && donation.target_level !== req.user.school_level) {
    return res.status(403).json({ error: 'Bu bağış sizin okul seviyenize uygun değil.' });
  }
  const already = db
    .prepare('SELECT id FROM claims WHERE donation_id = ? AND student_id = ?')
    .get(donation.id, req.user.id);
  if (already) return res.status(409).json({ error: 'Bu bağıştan zaten bir kitap aldınız.' });

  const check = checkCanReceive(req.user.id);
  if (!check.ok) return res.status(403).json({ error: check.reason, quota: check.quota });

  // Yarış durumlarına karşı işlemi transaction içinde, kalan adedi yeniden doğrulayarak yap
  const claim = db.transaction(() => {
    const count = db
      .prepare('SELECT COUNT(*) AS n FROM claims WHERE donation_id = ?')
      .get(donation.id).n;
    if (count >= donation.quantity) throw new Error('SOLD_OUT');
    const info = db
      .prepare('INSERT INTO claims (donation_id, student_id) VALUES (?, ?)')
      .run(donation.id, req.user.id);
    // Tamamen tükendiyse bağışı kapat
    if (count + 1 >= donation.quantity) {
      db.prepare("UPDATE donations SET status = 'closed' WHERE id = ?").run(donation.id);
    }
    return info.lastInsertRowid;
  });

  try {
    const claimId = claim();
    const updated = db.prepare(`${DONATION_SELECT} WHERE d.id = ?`).get(donation.id);
    res.status(201).json({ claim_id: claimId, donation: updated, quota: checkCanReceive(req.user.id).quota });
  } catch (err) {
    if (err.message === 'SOLD_OUT') {
      return res.status(409).json({ error: 'Bu bağışta kalan kitap kalmadı.' });
    }
    throw err;
  }
});

// Öğrencinin aldığı kitaplar
router.get('/claimed/mine', requireRole('student'), (req, res) => {
  const rows = db.prepare(`
    SELECT c.id AS claim_id, c.created_at, d.book_title, d.book_author, u.name AS donor_name
    FROM claims c
    JOIN donations d ON d.id = c.donation_id
    JOIN users u ON u.id = d.donor_id
    WHERE c.student_id = ? ORDER BY c.created_at DESC
  `).all(req.user.id);
  res.json(rows);
});

module.exports = router;
