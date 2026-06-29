'use strict';

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const os = require('os');

// İzole bir test veritabanı kullan
const tmpDb = path.join(os.tmpdir(), `kitap-test-${Date.now()}.db`);
process.env.DB_PATH = tmpDb;
process.env.JWT_SECRET = 'test-secret';

const app = require('../server');

let server;
let baseUrl;

before(async () => {
  await new Promise((resolve) => {
    server = app.listen(0, () => {
      baseUrl = `http://localhost:${server.address().port}`;
      resolve();
    });
  });
});

after(() => {
  server.close();
  for (const f of [tmpDb, `${tmpDb}-wal`, `${tmpDb}-shm`]) {
    if (fs.existsSync(f)) fs.unlinkSync(f);
  }
});

async function req(path, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: form ? form : body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

function studentForm(fields) {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) fd.append(k, v);
  fd.append('document', new Blob(['fake-document'], { type: 'application/pdf' }), 'belge.pdf');
  return fd;
}

test('bağışçı kaydı ve girişi', async () => {
  const r = await req('/api/auth/register/donor', {
    method: 'POST',
    body: { name: 'Ayşe Bağış', email: 'donor@test.com', password: 'sifre123' },
  });
  assert.strictEqual(r.status, 201);
  assert.ok(r.data.token);
  assert.strictEqual(r.data.user.role, 'donor');
});

test('aynı belge ile ikinci öğrenci kaydı reddedilir', async () => {
  const r1 = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Ali', email: 'ali@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-001' }),
  });
  assert.strictEqual(r1.status, 201);

  // Aynı belge numarası, farklı e-posta
  const r2 = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Veli', email: 'veli@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-001' }),
  });
  assert.strictEqual(r2.status, 409);
});

test('öğrenci belgesi olmadan kayıt reddedilir', async () => {
  const fd = new FormData();
  fd.append('name', 'Belgesiz');
  fd.append('email', 'belgesiz@test.com');
  fd.append('password', 'sifre123');
  fd.append('school_level', 'lise');
  fd.append('document_no', 'BELGE-XYZ');
  const r = await req('/api/auth/register/student', { method: 'POST', form: fd });
  assert.strictEqual(r.status, 400);
});

test('bağış akışı: oluştur, listele, talep et', async () => {
  const donor = await req('/api/auth/register/donor', {
    method: 'POST', body: { name: 'Bağışçı2', email: 'donor2@test.com', password: 'sifre123' },
  });
  const student = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Zeynep', email: 'zeynep@test.com', password: 'sifre123', school_level: 'universite', document_no: 'BELGE-002' }),
  });

  const created = await req('/api/donations', {
    method: 'POST', token: donor.data.token,
    body: { book_title: 'Sefiller', quantity: 2, target_level: 'universite' },
  });
  assert.strictEqual(created.status, 201);
  assert.strictEqual(created.data.remaining, 2);

  const claim = await req(`/api/donations/${created.data.id}/claim`, { method: 'POST', token: student.data.token });
  assert.strictEqual(claim.status, 201);
  assert.strictEqual(claim.data.donation.remaining, 1);

  // Aynı öğrenci aynı bağıştan tekrar alamaz
  const claim2 = await req(`/api/donations/${created.data.id}/claim`, { method: 'POST', token: student.data.token });
  assert.strictEqual(claim2.status, 409);
});

test('haftalık 3 kitap sınırı uygulanır', async () => {
  const donor = await req('/api/auth/register/donor', {
    method: 'POST', body: { name: 'BağışçıLimit', email: 'donorlim@test.com', password: 'sifre123' },
  });
  const student = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Limit', email: 'limit@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-LIM' }),
  });
  const token = student.data.token;

  // 4 ayrı bağış oluştur, öğrenci 3 tane alabilmeli, 4.'sü reddedilmeli
  for (let i = 0; i < 4; i++) {
    const d = await req('/api/donations', {
      method: 'POST', token: donor.data.token,
      body: { book_title: `Kitap ${i}`, quantity: 1, target_level: 'lise' },
    });
    const claim = await req(`/api/donations/${d.data.id}/claim`, { method: 'POST', token });
    if (i < 3) assert.strictEqual(claim.status, 201, `iddia ${i} başarılı olmalı`);
    else assert.strictEqual(claim.status, 403, '4. kitap haftalık sınır nedeniyle reddedilmeli');
  }

  const quota = await req('/api/me/quota', { token });
  assert.strictEqual(quota.data.weeklyUsed, 3);
  assert.strictEqual(quota.data.weeklyRemaining, 0);
});

test('istek akışı: öğrenci oluşturur, bağışçı karşılar', async () => {
  const donor = await req('/api/auth/register/donor', {
    method: 'POST', body: { name: 'BağışçıİstekKarşıla', email: 'donorreq@test.com', password: 'sifre123' },
  });
  const student = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'İstekçi', email: 'istek@test.com', password: 'sifre123', school_level: 'ortaokul', document_no: 'BELGE-REQ' }),
  });

  const created = await req('/api/requests', {
    method: 'POST', token: student.data.token, body: { book_title: 'Matematik 8' },
  });
  assert.strictEqual(created.status, 201);
  assert.strictEqual(created.data.status, 'open');

  const fulfill = await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donor.data.token });
  assert.strictEqual(fulfill.status, 200);
  assert.strictEqual(fulfill.data.status, 'fulfilled');

  // İkinci kez karşılanamaz
  const again = await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donor.data.token });
  assert.strictEqual(again.status, 409);
});

test('öğrenci bağış oluşturamaz (rol kontrolü)', async () => {
  const student = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Rol', email: 'rol@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-ROL' }),
  });
  const r = await req('/api/donations', {
    method: 'POST', token: student.data.token, body: { book_title: 'X', quantity: 1 },
  });
  assert.strictEqual(r.status, 403);
});
