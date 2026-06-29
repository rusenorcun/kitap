'use strict';

const path = require('path');
const express = require('express');

const { authenticate, requireRole } = require('./src/auth');
const { getQuota } = require('./src/limits');
const authRoutes = require('./src/routes/auth');
const donationRoutes = require('./src/routes/donations');
const requestRoutes = require('./src/routes/requests');

const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(authenticate);

// API
app.use('/api/auth', authRoutes);
app.use('/api/donations', donationRoutes);
app.use('/api/requests', requestRoutes);

// Giriş yapan öğrencinin kalan bağış hakkı (kota)
app.get('/api/me/quota', requireRole('student'), (req, res) => {
  res.json(getQuota(req.user.id));
});

app.get('/api/health', (req, res) => res.json({ status: 'ok' }));

// Statik arayüz
app.use(express.static(path.join(__dirname, 'public')));

// Hata yakalama (multer ve diğer hatalar)
app.use((err, req, res, next) => {
  if (res.headersSent) return next(err);
  const status = err.status || (err.message && err.message.includes('Belge') ? 400 : 500);
  res.status(status).json({ error: err.message || 'Sunucu hatası.' });
});

const PORT = process.env.PORT || 3000;

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`Kitap bağış platformu http://localhost:${PORT} adresinde çalışıyor`);
  });
}

module.exports = app;
