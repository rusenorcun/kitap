'use strict';

const express = require('express');
const db = require('../db');
const { requireAuth, requireDeliverable } = require('../auth');
const { notify } = require('../notifications');
const { cleanStr } = require('../validate');

const router = express.Router();

// ---------- Takasa açık kitaplar (swap_books) ----------

const SWAP_BOOK_SELECT = `
  SELECT s.*, u.name AS owner_name, b.title AS book_title, b.author AS book_author, b.cover_url
  FROM swap_books s
  JOIN users u ON u.id = s.user_id
  JOIN books b ON b.id = s.book_id
`;

// Başkalarının takasa açtığı kitaplar (keşif). Filtre: ?q=
router.get('/books', requireAuth, (req, res) => {
  const q = (req.query.q || '').trim();
  const where = ["s.status = 'open'", 's.user_id != @me'];
  const params = { me: req.user.id };
  if (q) { where.push('(b.title LIKE @like OR b.author LIKE @like)'); params.like = `%${q}%`; }
  res.json(db.prepare(`${SWAP_BOOK_SELECT} WHERE ${where.join(' AND ')} ORDER BY s.created_at DESC`).all(params));
});

// Kendi takas kitaplarım
router.get('/books/mine', requireAuth, (req, res) => {
  res.json(db.prepare(`${SWAP_BOOK_SELECT} WHERE s.user_id = ? ORDER BY s.created_at DESC`).all(req.user.id));
});

// Kitabımı takasa aç
router.post('/books', requireAuth, (req, res) => {
  const { book_id } = req.body || {};
  const note = cleanStr(req.body && req.body.note, 300);
  if (!book_id) return res.status(400).json({ error: 'Kitap (book_id) zorunludur.' });
  if (!db.prepare('SELECT id FROM books WHERE id = ?').get(book_id)) return res.status(404).json({ error: 'Kitap bulunamadı.' });
  if (db.prepare('SELECT id FROM swap_books WHERE user_id = ? AND book_id = ?').get(req.user.id, book_id)) {
    return res.status(409).json({ error: 'Bu kitabı zaten takasa açtınız.' });
  }
  const info = db.prepare('INSERT INTO swap_books (user_id, book_id, note) VALUES (?, ?, ?)').run(req.user.id, book_id, note);
  res.status(201).json(db.prepare(`${SWAP_BOOK_SELECT} WHERE s.id = ?`).get(info.lastInsertRowid));
});

// Takas kitabını aç/kapat
router.patch('/books/:id', requireAuth, (req, res) => {
  const row = db.prepare('SELECT * FROM swap_books WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
  if (!row) return res.status(404).json({ error: 'Takas kitabı bulunamadı.' });
  const status = req.body && req.body.status;
  if (!['open', 'closed'].includes(status)) return res.status(400).json({ error: "status 'open' veya 'closed' olmalı." });
  db.prepare('UPDATE swap_books SET status = ? WHERE id = ?').run(status, row.id);
  res.json(db.prepare(`${SWAP_BOOK_SELECT} WHERE s.id = ?`).get(row.id));
});

// Takas kitabını kaldır
router.delete('/books/:id', requireAuth, (req, res) => {
  const row = db.prepare('SELECT * FROM swap_books WHERE id = ? AND user_id = ?').get(req.params.id, req.user.id);
  if (!row) return res.status(404).json({ error: 'Takas kitabı bulunamadı.' });
  if (db.prepare("SELECT id FROM swap_offers WHERE (offered_swap_book_id = ? OR target_swap_book_id = ?) AND status = 'accepted'").get(row.id, row.id)) {
    return res.status(409).json({ error: 'Kabul edilmiş bir takasa bağlı kitap kaldırılamaz.' });
  }
  db.prepare('DELETE FROM swap_books WHERE id = ?').run(row.id);
  res.json({ ok: true });
});

// ---------- Takas teklifleri (swap_offers) ----------

const OFFER_SELECT = `
  SELECT o.*,
         fu.name AS from_name, fu.address AS from_address, fu.phone AS from_phone,
         tu.name AS to_name, tu.address AS to_address, tu.phone AS to_phone,
         ob.title AS offered_title, ob.author AS offered_author, ob.cover_url AS offered_cover,
         tb.title AS target_title, tb.author AS target_author, tb.cover_url AS target_cover
  FROM swap_offers o
  JOIN users fu ON fu.id = o.from_user_id
  JOIN users tu ON tu.id = o.to_user_id
  JOIN swap_books osb ON osb.id = o.offered_swap_book_id
  JOIN books ob ON ob.id = osb.book_id
  JOIN swap_books tsb ON tsb.id = o.target_swap_book_id
  JOIN books tb ON tb.id = tsb.book_id
`;

// Adresler yalnızca kabul edilmiş/tamamlanmış takasta iki tarafa açılır.
function shapeOffer(o) {
  if (!o) return o;
  const shared = o.status === 'accepted' || o.status === 'completed';
  return {
    ...o,
    from_address: shared ? o.from_address : undefined,
    from_phone: shared ? o.from_phone : undefined,
    to_address: shared ? o.to_address : undefined,
    to_phone: shared ? o.to_phone : undefined,
  };
}

// Teklif ver: kendi takas kitabını, hedef kitabın sahibine sun
router.post('/offers', requireDeliverable, (req, res) => {
  const { target_swap_book_id, offered_swap_book_id } = req.body || {};
  const message = cleanStr(req.body && req.body.message, 300);
  if (!target_swap_book_id || !offered_swap_book_id) {
    return res.status(400).json({ error: 'Hedef ve teklif edilen takas kitabı zorunludur.' });
  }
  const target = db.prepare("SELECT * FROM swap_books WHERE id = ? AND status = 'open'").get(target_swap_book_id);
  if (!target) return res.status(404).json({ error: 'Hedef takas kitabı bulunamadı veya kapalı.' });
  if (target.user_id === req.user.id) return res.status(400).json({ error: 'Kendi kitabınıza teklif veremezsiniz.' });

  const offered = db.prepare("SELECT * FROM swap_books WHERE id = ? AND user_id = ? AND status = 'open'").get(offered_swap_book_id, req.user.id);
  if (!offered) return res.status(404).json({ error: 'Teklif edeceğiniz kitap size ait ve açık olmalı.' });

  const dupe = db.prepare("SELECT id FROM swap_offers WHERE from_user_id = ? AND target_swap_book_id = ? AND status = 'pending'")
    .get(req.user.id, target_swap_book_id);
  if (dupe) return res.status(409).json({ error: 'Bu kitap için zaten bekleyen bir teklifiniz var.' });

  const info = db.prepare(`INSERT INTO swap_offers (from_user_id, to_user_id, offered_swap_book_id, target_swap_book_id, message)
                           VALUES (?, ?, ?, ?, ?)`)
    .run(req.user.id, target.user_id, offered_swap_book_id, target_swap_book_id, message);
  const offer = db.prepare(`${OFFER_SELECT} WHERE o.id = ?`).get(info.lastInsertRowid);
  notify(target.user_id, 'swap_offer', `${req.user.name} sana takas teklif etti: "${offer.offered_title}" ↔ "${offer.target_title}"`, { offer_id: offer.id });
  res.status(201).json(shapeOffer(offer));
});

// Bana gelen teklifler
router.get('/offers/incoming', requireAuth, (req, res) => {
  res.json(db.prepare(`${OFFER_SELECT} WHERE o.to_user_id = ? ORDER BY o.created_at DESC`).all(req.user.id).map(shapeOffer));
});

// Gönderdiğim teklifler
router.get('/offers/outgoing', requireAuth, (req, res) => {
  res.json(db.prepare(`${OFFER_SELECT} WHERE o.from_user_id = ? ORDER BY o.created_at DESC`).all(req.user.id).map(shapeOffer));
});

// Teklifi kabul et (hedef sahibi) — iki kitabı kapat, adresleri paylaş
router.post('/offers/:id/accept', requireDeliverable, (req, res) => {
  const offer = db.prepare('SELECT * FROM swap_offers WHERE id = ? AND to_user_id = ?').get(req.params.id, req.user.id);
  if (!offer) return res.status(404).json({ error: 'Teklif bulunamadı.' });
  if (offer.status !== 'pending') return res.status(409).json({ error: 'Bu teklif zaten yanıtlanmış.' });
  db.transaction(() => {
    db.prepare("UPDATE swap_offers SET status = 'accepted', decided_at = datetime('now') WHERE id = ?").run(offer.id);
    db.prepare("UPDATE swap_books SET status = 'closed' WHERE id IN (?, ?)").run(offer.offered_swap_book_id, offer.target_swap_book_id);
    // Aynı kitaplara başka bekleyen teklifleri reddet
    db.prepare(`UPDATE swap_offers SET status = 'rejected', decided_at = datetime('now')
                WHERE status = 'pending' AND id != ?
                  AND (target_swap_book_id IN (?, ?) OR offered_swap_book_id IN (?, ?))`)
      .run(offer.id, offer.offered_swap_book_id, offer.target_swap_book_id, offer.offered_swap_book_id, offer.target_swap_book_id);
  })();
  const full = db.prepare(`${OFFER_SELECT} WHERE o.id = ?`).get(offer.id);
  notify(offer.from_user_id, 'swap_accepted', `${req.user.name} takas teklifini kabul etti: "${full.offered_title}" ↔ "${full.target_title}". Adresler paylaşıldı.`, { offer_id: offer.id });
  res.json(shapeOffer(full));
});

// Teklifi reddet (hedef sahibi)
router.post('/offers/:id/reject', requireAuth, (req, res) => {
  const offer = db.prepare('SELECT * FROM swap_offers WHERE id = ? AND to_user_id = ?').get(req.params.id, req.user.id);
  if (!offer) return res.status(404).json({ error: 'Teklif bulunamadı.' });
  if (offer.status !== 'pending') return res.status(409).json({ error: 'Bu teklif zaten yanıtlanmış.' });
  db.prepare("UPDATE swap_offers SET status = 'rejected', decided_at = datetime('now') WHERE id = ?").run(offer.id);
  const full = db.prepare(`${OFFER_SELECT} WHERE o.id = ?`).get(offer.id);
  notify(offer.from_user_id, 'swap_rejected', `Takas teklifin reddedildi: "${full.offered_title}" ↔ "${full.target_title}".`, { offer_id: offer.id });
  res.json({ ok: true, status: 'rejected' });
});

// Teklifi geri çek (teklif eden)
router.post('/offers/:id/cancel', requireAuth, (req, res) => {
  const offer = db.prepare('SELECT * FROM swap_offers WHERE id = ? AND from_user_id = ?').get(req.params.id, req.user.id);
  if (!offer) return res.status(404).json({ error: 'Teklif bulunamadı.' });
  if (offer.status !== 'pending') return res.status(409).json({ error: 'Yalnızca bekleyen teklif geri çekilebilir.' });
  db.prepare("UPDATE swap_offers SET status = 'cancelled', decided_at = datetime('now') WHERE id = ?").run(offer.id);
  res.json({ ok: true, status: 'cancelled' });
});

// Kargoya verdim (iki taraftan biri) — ikisi de kargolayınca tamamlanır
router.post('/offers/:id/ship', requireAuth, (req, res) => {
  const offer = db.prepare('SELECT * FROM swap_offers WHERE id = ?').get(req.params.id);
  if (!offer || (offer.from_user_id !== req.user.id && offer.to_user_id !== req.user.id)) {
    return res.status(404).json({ error: 'Teklif bulunamadı.' });
  }
  if (offer.status !== 'accepted') return res.status(409).json({ error: 'Yalnızca kabul edilmiş takas kargolanabilir.' });
  const isFrom = offer.from_user_id === req.user.id;
  if ((isFrom && offer.from_shipped_at) || (!isFrom && offer.to_shipped_at)) {
    return res.status(409).json({ error: 'Zaten kargoya verdiniz.' });
  }
  const col = isFrom ? 'from_shipped_at' : 'to_shipped_at';
  db.prepare(`UPDATE swap_offers SET ${col} = datetime('now') WHERE id = ?`).run(offer.id);

  const updated = db.prepare('SELECT * FROM swap_offers WHERE id = ?').get(offer.id);
  const other = isFrom ? offer.to_user_id : offer.from_user_id;
  const full = db.prepare(`${OFFER_SELECT} WHERE o.id = ?`).get(offer.id);
  notify(other, 'swap_shipped', `${req.user.name} takas kitabını kargoya verdi.`, { offer_id: offer.id });

  if (updated.from_shipped_at && updated.to_shipped_at) {
    db.prepare("UPDATE swap_offers SET status = 'completed', decided_at = datetime('now') WHERE id = ?").run(offer.id);
    notify(offer.from_user_id, 'swap_completed', `Takas tamamlandı: "${full.offered_title}" ↔ "${full.target_title}".`, { offer_id: offer.id });
    notify(offer.to_user_id, 'swap_completed', `Takas tamamlandı: "${full.target_title}" ↔ "${full.offered_title}".`, { offer_id: offer.id });
  }
  res.json({ ok: true });
});

module.exports = router;
