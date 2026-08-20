'use strict';

const express = require('express');

const { authenticate } = require('./src/auth');
const { seedAdmin } = require('./src/seed');
const authRoutes = require('./src/routes/auth');
const bookRoutes = require('./src/routes/books');
const donationRoutes = require('./src/routes/donations');
const requestRoutes = require('./src/routes/requests');
const adminRoutes = require('./src/routes/admin');
const meRoutes = require('./src/routes/me');
const publicRoutes = require('./src/routes/public');

const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(authenticate);

// API kökü — istemci (mobil/web) bu uçları kullanır
app.get('/', (req, res) => {
  res.json({
    name: 'Kitap Bağış Platformu API',
    version: '2.0.0',
    docs: 'README.md',
    endpoints: ['/api/auth', '/api/books', '/api/donations', '/api/requests', '/api/admin', '/api/me', '/api/stats'],
  });
});
app.get('/api/health', (req, res) => res.json({ status: 'ok' }));

app.use('/api/auth', authRoutes);
app.use('/api/books', bookRoutes);
app.use('/api/donations', donationRoutes);
app.use('/api/requests', requestRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/me', meRoutes);
app.use('/api', publicRoutes);

// Hata yakalama (multer ve diğer hatalar)
app.use((err, req, res, next) => {
  if (res.headersSent) return next(err);
  const isUpload = err.message && (err.message.includes('Belge') || err.message.includes('Kapak'));
  const status = err.status || err.statusCode || (isUpload ? 400 : 500);
  res.status(status).json({ error: err.message || 'Sunucu hatası.' });
});

const PORT = process.env.PORT || 3000;

if (require.main === module) {
  try {
    const adminId = seedAdmin();
    if (adminId) console.log(`Admin hesabı hazır (id=${adminId}).`);
  } catch (err) {
    console.error('Admin seed hatası:', err.message);
  }
  app.listen(PORT, () => {
    console.log(`Kitap bağış platformu API http://localhost:${PORT} adresinde çalışıyor`);
  });
}

module.exports = app;
