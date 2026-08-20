'use strict';

const express = require('express');
const db = require('../db');

const router = express.Router();

// Kamuya açık özet istatistikler (ana sayfa için) — kişisel veri içermez.
router.get('/stats', (req, res) => {
  const one = (sql) => db.prepare(sql).get().n;
  const delivered =
    one("SELECT COUNT(*) AS n FROM claims WHERE status = 'delivered'") +
    one("SELECT COUNT(*) AS n FROM requests WHERE status = 'delivered'");
  res.json({
    books: one('SELECT COUNT(*) AS n FROM books'),
    donations: one('SELECT COUNT(*) AS n FROM donations'),
    openDonations: one("SELECT COUNT(*) AS n FROM donations WHERE status = 'open'"),
    openRequests: one("SELECT COUNT(*) AS n FROM requests WHERE status = 'open'"),
    booksDelivered: delivered,
    swapsCompleted: one("SELECT COUNT(*) AS n FROM swap_offers WHERE status = 'completed'"),
    members: one('SELECT COUNT(*) AS n FROM users WHERE is_admin = 0 AND blocked = 0'),
    students: one("SELECT COUNT(*) AS n FROM users WHERE student_status = 'approved' AND blocked = 0"),
  });
});

module.exports = router;
