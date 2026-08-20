'use strict';

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const os = require('os');

const tmpDb = path.join(os.tmpdir(), `kitap-test-${Date.now()}.db`);
process.env.DB_PATH = tmpDb;
process.env.JWT_SECRET = 'test-secret';
process.env.ADMIN_EMAIL = 'admin@test.com';
process.env.ADMIN_PASSWORD = 'admin123';

const app = require('../server');
const db = require('../src/db');
const { seedAdmin } = require('../src/seed');
const { parseOgTags } = require('../src/og');

let server;
let baseUrl;
let adminToken;

before(async () => {
  seedAdmin();
  await new Promise((resolve) => {
    server = app.listen(0, () => { baseUrl = `http://localhost:${server.address().port}`; resolve(); });
  });
  adminToken = (await req('/api/auth/login', { method: 'POST', body: { email: 'admin@test.com', password: 'admin123' } })).data.token;
});

after(() => {
  server.close();
  for (const f of [tmpDb, `${tmpDb}-wal`, `${tmpDb}-shm`]) if (fs.existsSync(f)) fs.unlinkSync(f);
});

async function req(p, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${baseUrl}${p}`, { method, headers, body: form || (body ? JSON.stringify(body) : undefined) });
  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

// Üye kaydı (belgesiz). Varsayılan adres verilir (almak/takas için).
async function registerMember(email, { name = 'Üye', address = 'Adres 1', password = 'sifre123' } = {}) {
  const r = await req('/api/auth/register', { method: 'POST', body: { name, email, password, address } });
  assert.strictEqual(r.status, 201, `üye kaydı: ${JSON.stringify(r.data)}`);
  return { token: r.data.token, id: r.data.user.id, user: r.data.user };
}

function studentForm(fields) {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) fd.append(k, v);
  fd.append('document', new Blob(['belge'], { type: 'application/pdf' }), 'belge.pdf');
  return fd;
}

// Onaylı öğrenci: belgeli kayıt + admin onayı + taze token
async function approvedStudent(email, { school_level = 'lise', document_no, address = 'Öğrenci Adres', password = 'sifre123' } = {}) {
  const reg = await req('/api/auth/register', { method: 'POST', form: studentForm({ name: 'Öğrenci', email, password, school_level, document_no: document_no || `DOC-${email}`, address }) });
  assert.strictEqual(reg.status, 201, `öğrenci kaydı: ${JSON.stringify(reg.data)}`);
  await req(`/api/admin/users/${reg.data.user.id}/approve`, { method: 'POST', token: adminToken });
  const login = await req('/api/auth/login', { method: 'POST', body: { email, password } });
  return { token: login.data.token, id: reg.data.user.id };
}

async function makeBook(token, title, author) {
  const r = await req('/api/books', { method: 'POST', token, body: { title, author } });
  return r.data.book.id;
}
function backdateDonation(id) {
  db.prepare("UPDATE donations SET created_at = datetime('now', '-3 days') WHERE id = ?").run(id);
}

// ---------- OpenGraph ----------
test('parseOgTags og:title ve og:image çıkarır', () => {
  const m = parseOgTags(`<meta property="og:title" content="Sefiller &amp; Devam"><meta content="https://x/c.jpg" property="og:image">`);
  assert.strictEqual(m.title, 'Sefiller & Devam');
  assert.strictEqual(m.image, 'https://x/c.jpg');
});

// ---------- Kayıt / roller ----------
test('üye kaydı olur ve bağış yapabilir', async () => {
  const m = await registerMember('uye@test.com');
  assert.strictEqual(m.user.isStudent, false);
  assert.strictEqual(m.user.recipientTier, 'member');
  assert.strictEqual(m.user.canDonate, true);
  const bookId = await makeBook(m.token, 'Üye Kitabı', 'Yazar');
  const don = await req('/api/donations', { method: 'POST', token: m.token, body: { book_id: bookId, quantity: 1 } });
  assert.strictEqual(don.status, 201);
});

test('geçersiz e-posta reddedilir', async () => {
  const r = await req('/api/auth/register', { method: 'POST', body: { name: 'X', email: 'gecersiz', password: 'sifre123' } });
  assert.strictEqual(r.status, 400);
});

test('aynı belge ile ikinci öğrenci kaydı reddedilir', async () => {
  const r1 = await req('/api/auth/register', { method: 'POST', form: studentForm({ name: 'A', email: 'a@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-1', address: 'Adr' }) });
  assert.strictEqual(r1.status, 201);
  assert.strictEqual(r1.data.user.studentStatus, 'pending');
  const r2 = await req('/api/auth/register', { method: 'POST', form: studentForm({ name: 'B', email: 'b@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-1', address: 'Adr' }) });
  assert.strictEqual(r2.status, 409);
});

test('üye sonradan öğrenci doğrulamasına başvurur ve admin onaylar', async () => {
  const m = await registerMember('yukselen@test.com', { address: 'Adres' });
  const verify = await req('/api/me/verify-student', { method: 'POST', token: m.token, form: studentForm({ school_level: 'universite', document_no: 'BELGE-UP' }) });
  assert.strictEqual(verify.status, 202);
  assert.strictEqual(verify.data.studentStatus, 'pending');
  const approve = await req(`/api/admin/users/${m.id}/approve`, { method: 'POST', token: adminToken });
  assert.strictEqual(approve.status, 200);
  const me = await req('/api/me', { token: m.token });
  assert.strictEqual(me.data.isStudent, true);
  assert.strictEqual(me.data.recipientTier, 'student');
});

// ---------- Öğrenci önceliği ----------
test('yeni bağış öncelik penceresinde üyeye kapalı, öğrenciye açık', async () => {
  const donor = await registerMember('donor-pri@test.com', { address: 'D' });
  const bookId = await makeBook(donor.token, 'Öncelik Kitabı', 'Y');
  const don = await req('/api/donations', { method: 'POST', token: donor.token, body: { book_id: bookId, quantity: 3 } });
  assert.strictEqual(don.data.priority_active, true);

  const member = await registerMember('member-pri@test.com', { address: 'M' });
  const blocked = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: member.token });
  assert.strictEqual(blocked.status, 403);
  assert.strictEqual(blocked.data.code, 'PRIORITY_WINDOW');

  const student = await approvedStudent('student-pri@test.com', { document_no: 'DOC-PRI' });
  const ok = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  assert.strictEqual(ok.status, 201);

  // Pencere geçince üye de alabilir
  backdateDonation(don.data.id);
  const memberOk = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: member.token });
  assert.strictEqual(memberOk.status, 201);
});

test('üye haftalık 1, öğrenci haftalık 3 kota', async () => {
  const donor = await registerMember('donor-quota@test.com', { address: 'D' });
  const member = await registerMember('member-quota@test.com', { address: 'M' });
  const q0 = await req('/api/me/quota', { token: member.token });
  assert.strictEqual(q0.data.weeklyLimit, 1);
  assert.strictEqual(q0.data.tier, 'member');

  // İki (pencere geçmiş) bağış: üye ilkini alır, ikincide kota dolar
  for (let i = 0; i < 2; i++) {
    const bookId = await makeBook(donor.token, `Kota Kitap ${i}`, 'Y');
    const don = await req('/api/donations', { method: 'POST', token: donor.token, body: { book_id: bookId, quantity: 1 } });
    backdateDonation(don.data.id);
    const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: member.token });
    if (i === 0) assert.strictEqual(claim.status, 201);
    else assert.strictEqual(claim.status, 403, 'üye 2. kitapta kota dolmalı');
  }
  const sq = await req('/api/me/quota', { token: (await approvedStudent('student-quota@test.com', { document_no: 'DOC-Q' })).token });
  assert.strictEqual(sq.data.weeklyLimit, 3);
});

test('adressiz üye kitap alamaz (ADDRESS_REQUIRED)', async () => {
  const noaddr = await req('/api/auth/register', { method: 'POST', body: { name: 'NoAddr', email: 'noaddr@test.com', password: 'sifre123' } });
  const donor = await registerMember('donor-na@test.com', { address: 'D' });
  const bookId = await makeBook(donor.token, 'NA Kitap', 'Y');
  const don = await req('/api/donations', { method: 'POST', token: donor.token, body: { book_id: bookId, quantity: 1 } });
  backdateDonation(don.data.id);
  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: noaddr.data.token });
  assert.strictEqual(claim.status, 403);
  assert.strictEqual(claim.data.code, 'ADDRESS_REQUIRED');
});

// ---------- Bağış teslimat + teşekkür ----------
test('bağış: al, adres paylaş, kargola, teslim al, teşekkür', async () => {
  const donor = await registerMember('donor-flow@test.com', { address: 'D' });
  const student = await approvedStudent('flow@test.com', { school_level: 'lise', document_no: 'DOC-FLOW', address: 'Teslim Adresi 5' });
  const bookId = await makeBook(donor.token, 'Beyaz Diş', 'Jack London');
  const don = await req('/api/donations', { method: 'POST', token: donor.token, body: { book_id: bookId, quantity: 1, source: 'own', target_level: 'lise' } });
  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  assert.strictEqual(claim.status, 201);
  const cid = claim.data.claim_id;

  const mine = await req('/api/donations/mine', { token: donor.token });
  assert.strictEqual(mine.data[0].claimers[0].address, 'Teslim Adresi 5');

  assert.strictEqual((await req(`/api/donations/claims/${cid}/ship`, { method: 'POST', token: donor.token })).status, 200);
  assert.strictEqual((await req(`/api/donations/claims/${cid}/deliver`, { method: 'POST', token: student.token })).status, 200);
  assert.strictEqual((await req(`/api/donations/claims/${cid}/thank`, { method: 'POST', token: student.token, body: { message: 'Teşekkürler' } })).status, 201);
  const notifs = await req('/api/me/notifications', { token: donor.token });
  assert.ok(notifs.data.some((n) => n.type === 'thank_you'));
});

test('talep iptali adedi geri açar', async () => {
  const donor = await registerMember('donor-cancel@test.com', { address: 'D' });
  const student = await approvedStudent('cancel@test.com', { document_no: 'DOC-CANCEL' });
  const bookId = await makeBook(donor.token, 'İptal Kitabı', 'Z');
  const don = await req('/api/donations', { method: 'POST', token: donor.token, body: { book_id: bookId, quantity: 1 } });
  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  assert.strictEqual(claim.data.donation.remaining, 0);
  assert.strictEqual((await req(`/api/donations/claims/${claim.data.claim_id}`, { method: 'DELETE', token: student.token })).status, 200);
  const list = await req('/api/donations');
  assert.ok(list.data.some((d) => d.id === don.data.id && d.remaining === 1));
});

// ---------- İstekler ----------
test('istek: oluştur, karşıla, adres paylaş', async () => {
  const donor = await registerMember('donor-req@test.com', { address: 'D' });
  const student = await approvedStudent('istek@test.com', { school_level: 'ortaokul', document_no: 'DOC-REQ', address: 'İstek Adresi 9' });
  const bookId = await makeBook(donor.token, 'Matematik 8', null);
  const created = await req('/api/requests', { method: 'POST', token: student.token, body: { book_id: bookId } });
  assert.strictEqual(created.status, 201);
  const open = await req('/api/requests?status=open');
  assert.ok(open.data.every((r) => !('address' in r)));
  const fulfill = await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donor.token, body: { source: 'purchase' } });
  assert.strictEqual(fulfill.status, 200);
  const fulfilled = await req('/api/requests/fulfilled/mine', { token: donor.token });
  assert.strictEqual(fulfilled.data[0].address, 'İstek Adresi 9');
  assert.strictEqual((await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donor.token })).status, 409);
});

// ---------- TAKAS ----------
test('takas: teklif, kabul (adres paylaşımı), çift kargo ile tamamlama', async () => {
  const ali = await registerMember('ali-swap@test.com', { name: 'Ali', address: 'Ali Adres' });
  const veli = await registerMember('veli-swap@test.com', { name: 'Veli', address: 'Veli Adres' });
  const book1984 = await makeBook(ali.token, '1984', 'Orwell');
  const bookSimya = await makeBook(veli.token, 'Simyacı', 'Coelho');

  const aliSwap = await req('/api/swaps/books', { method: 'POST', token: ali.token, body: { book_id: book1984, note: 'Distopya isterim' } });
  const veliSwap = await req('/api/swaps/books', { method: 'POST', token: veli.token, body: { book_id: bookSimya } });
  assert.strictEqual(aliSwap.status, 201);

  // Veli, Ali'nin kitabını keşifte görür
  const disc = await req('/api/swaps/books', { token: veli.token });
  assert.ok(disc.data.some((s) => s.id === aliSwap.data.id));

  // Veli teklif verir: Simyacı ↔ 1984
  const offer = await req('/api/swaps/offers', { method: 'POST', token: veli.token, body: { target_swap_book_id: aliSwap.data.id, offered_swap_book_id: veliSwap.data.id, message: 'Olur mu?' } });
  assert.strictEqual(offer.status, 201);
  // Bekleyen teklifte adres gizli
  const incoming0 = await req('/api/swaps/offers/incoming', { token: ali.token });
  assert.strictEqual(incoming0.data[0].from_address, undefined);
  assert.ok(ali && (await req('/api/me/notifications', { token: ali.token })).data.some((n) => n.type === 'swap_offer'));

  // Duplicate teklif reddi
  const dup = await req('/api/swaps/offers', { method: 'POST', token: veli.token, body: { target_swap_book_id: aliSwap.data.id, offered_swap_book_id: veliSwap.data.id } });
  assert.strictEqual(dup.status, 409);

  // Ali kabul eder → adresler paylaşılır, kitaplar kapanır
  const accept = await req(`/api/swaps/offers/${offer.data.id}/accept`, { method: 'POST', token: ali.token });
  assert.strictEqual(accept.status, 200);
  assert.strictEqual(accept.data.from_address, 'Veli Adres');
  assert.strictEqual(accept.data.to_address, 'Ali Adres');

  // Çift kargo → tamamlanır
  assert.strictEqual((await req(`/api/swaps/offers/${offer.data.id}/ship`, { method: 'POST', token: ali.token })).status, 200);
  const finalShip = await req(`/api/swaps/offers/${offer.data.id}/ship`, { method: 'POST', token: veli.token });
  assert.strictEqual(finalShip.status, 200);
  const outgoing = await req('/api/swaps/offers/outgoing', { token: veli.token });
  assert.strictEqual(outgoing.data[0].status, 'completed');
});

test('takas teklifi reddedilebilir', async () => {
  const a = await registerMember('a-rej@test.com', { address: 'A' });
  const b = await registerMember('b-rej@test.com', { address: 'B' });
  const ba = await makeBook(a.token, 'Kitap A', 'x');
  const bb = await makeBook(b.token, 'Kitap B', 'x');
  const sa = await req('/api/swaps/books', { method: 'POST', token: a.token, body: { book_id: ba } });
  const sb = await req('/api/swaps/books', { method: 'POST', token: b.token, body: { book_id: bb } });
  const offer = await req('/api/swaps/offers', { method: 'POST', token: b.token, body: { target_swap_book_id: sa.data.id, offered_swap_book_id: sb.data.id } });
  const rej = await req(`/api/swaps/offers/${offer.data.id}/reject`, { method: 'POST', token: a.token });
  assert.strictEqual(rej.status, 200);
  assert.ok((await req('/api/me/notifications', { token: b.token })).data.some((n) => n.type === 'swap_rejected'));
});

// ---------- Admin ----------
test('admin engelleme, silme, öğrenci reddi', async () => {
  const m = await registerMember('block@test.com', { address: 'A' });
  assert.strictEqual((await req(`/api/admin/users/${m.id}/block`, { method: 'POST', token: adminToken })).status, 200);
  assert.strictEqual((await req('/api/auth/login', { method: 'POST', body: { email: 'block@test.com', password: 'sifre123' } })).status, 403);
  await req(`/api/admin/users/${m.id}/unblock`, { method: 'POST', token: adminToken });
  assert.strictEqual((await req(`/api/admin/users/${m.id}`, { method: 'DELETE', token: adminToken })).status, 200);

  const s = await req('/api/auth/register', { method: 'POST', form: studentForm({ name: 'Red', email: 'red@test.com', password: 'sifre123', school_level: 'lise', document_no: 'DOC-RED', address: 'A' }) });
  const reject = await req(`/api/admin/users/${s.data.user.id}/reject`, { method: 'POST', token: adminToken });
  assert.strictEqual(reject.status, 200);
  assert.ok((await req('/api/me/notifications', { token: s.data.token })).data.some((n) => n.type === 'document_rejected'));
});

test('admin olmayan admin uçlarına erişemez', async () => {
  const m = await registerMember('noadmin@test.com', { address: 'A' });
  assert.strictEqual((await req('/api/admin/users', { token: m.token })).status, 403);
});

test('genel istatistikler ve içerik listeleri', async () => {
  const stats = await req('/api/stats');
  assert.strictEqual(stats.status, 200);
  assert.ok('swapsCompleted' in stats.data);
  const admStats = await req('/api/admin/stats', { token: adminToken });
  assert.ok('swapBooks' in admStats.data);
  assert.strictEqual((await req('/api/admin/donations', { token: adminToken })).status, 200);
  assert.strictEqual((await req('/api/admin/requests', { token: adminToken })).status, 200);
});

test('kitap find-or-create tekilliği', async () => {
  const m = await registerMember('book@test.com', { address: 'A' });
  const first = await req('/api/books', { method: 'POST', token: m.token, body: { title: 'Yabancı', author: 'Camus' } });
  assert.strictEqual(first.data.existed, false);
  const dup = await req('/api/books', { method: 'POST', token: m.token, body: { title: 'yabancı', author: 'camus' } });
  assert.strictEqual(dup.data.existed, true);
  assert.strictEqual(dup.data.book.id, first.data.book.id);
});

test('giriş oran sınırı devreye girer', async () => {
  await registerMember('brute@test.com', { address: 'A' });
  let limited = false;
  for (let i = 0; i < 10; i++) {
    const r = await req('/api/auth/login', { method: 'POST', body: { email: 'brute@test.com', password: 'yanlis' } });
    if (r.status === 429) { limited = true; break; }
  }
  assert.ok(limited);
});
