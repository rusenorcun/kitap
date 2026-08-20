'use strict';

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const os = require('os');

// İzole bir test veritabanı + seed admin için ortam değişkenleri
const tmpDb = path.join(os.tmpdir(), `kitap-test-${Date.now()}.db`);
process.env.DB_PATH = tmpDb;
process.env.JWT_SECRET = 'test-secret';
process.env.ADMIN_EMAIL = 'admin@test.com';
process.env.ADMIN_PASSWORD = 'admin123';

const app = require('../server');
const { seedAdmin } = require('../src/seed');
const { parseOgTags } = require('../src/og');

let server;
let baseUrl;
let adminToken;

before(async () => {
  seedAdmin();
  await new Promise((resolve) => {
    server = app.listen(0, () => {
      baseUrl = `http://localhost:${server.address().port}`;
      resolve();
    });
  });
  const login = await req('/api/auth/login', { method: 'POST', body: { email: 'admin@test.com', password: 'admin123' } });
  adminToken = login.data.token;
});

after(() => {
  server.close();
  for (const f of [tmpDb, `${tmpDb}-wal`, `${tmpDb}-shm`]) {
    if (fs.existsSync(f)) fs.unlinkSync(f);
  }
});

async function req(p, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${baseUrl}${p}`, {
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

// Onaylı bir öğrenci oluşturur ve token döndürür
async function approvedStudent(fields) {
  const reg = await req('/api/auth/register/student', { method: 'POST', form: studentForm(fields) });
  assert.strictEqual(reg.status, 201, 'öğrenci kaydı oluşmalı');
  const id = reg.data.user.id;
  const approve = await req(`/api/admin/users/${id}/approve`, { method: 'POST', token: adminToken });
  assert.strictEqual(approve.status, 200);
  // onay sonrası taze token al
  const login = await req('/api/auth/login', { method: 'POST', body: { email: fields.email, password: fields.password } });
  return { id, token: login.data.token };
}

async function makeDonor(email) {
  const r = await req('/api/auth/register/donor', { method: 'POST', body: { name: 'Bağışçı', email, password: 'sifre123' } });
  return r.data.token;
}

async function makeBook(token, title, author) {
  const r = await req('/api/books', { method: 'POST', token, body: { title, author } });
  return r.data.book.id;
}

// ---------- OpenGraph ayrıştırıcı ----------
test('parseOgTags og:title ve og:image çıkarır', () => {
  const html = `<html><head>
    <meta property="og:title" content="Sefiller &amp; Devamı" />
    <meta content="https://x/cover.jpg" property="og:image">
    <title>Yedek Başlık</title>
  </head></html>`;
  const m = parseOgTags(html);
  assert.strictEqual(m.title, 'Sefiller & Devamı');
  assert.strictEqual(m.image, 'https://x/cover.jpg');
});

test('parseOgTags og yoksa <title> kullanır', () => {
  const m = parseOgTags('<html><head><title>Sadece Başlık</title></head></html>');
  assert.strictEqual(m.title, 'Sadece Başlık');
  assert.strictEqual(m.image, null);
});

// ---------- Admin seed ----------
test('seed admin ile giriş yapılır', async () => {
  assert.ok(adminToken, 'admin token alınmalı');
  const me = await req('/api/admin/stats', { token: adminToken });
  assert.strictEqual(me.status, 200);
});

// ---------- Kayıt ve belge kuralları ----------
test('aynı belge ile ikinci öğrenci kaydı reddedilir', async () => {
  const r1 = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Ali', email: 'ali@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-001', address: 'Adres 1' }),
  });
  assert.strictEqual(r1.status, 201);
  assert.strictEqual(r1.data.user.status, 'pending');

  const r2 = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Veli', email: 'veli@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-001', address: 'Adres 2' }),
  });
  assert.strictEqual(r2.status, 409);
});

test('adres olmadan öğrenci kaydı reddedilir', async () => {
  const fd = new FormData();
  fd.append('name', 'Adressiz');
  fd.append('email', 'adressiz@test.com');
  fd.append('password', 'sifre123');
  fd.append('school_level', 'lise');
  fd.append('document_no', 'BELGE-ADR');
  fd.append('document', new Blob(['x'], { type: 'application/pdf' }), 'b.pdf');
  const r = await req('/api/auth/register/student', { method: 'POST', form: fd });
  assert.strictEqual(r.status, 400);
});

// ---------- Belge onayı gating ----------
test('onaysız öğrenci kitap alamaz, onaydan sonra alabilir', async () => {
  const reg = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Onay', email: 'onay@test.com', password: 'sifre123', school_level: 'universite', document_no: 'BELGE-ONAY', address: 'Adres' }),
  });
  const sid = reg.data.user.id;
  const sToken = reg.data.token;

  const donorToken = await makeDonor('donor-onay@test.com');
  const bookId = await makeBook(donorToken, 'Suç ve Ceza', 'Dostoyevski');
  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 2, target_level: 'universite' } });

  // onaysızken 403
  const claimBefore = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: sToken });
  assert.strictEqual(claimBefore.status, 403);

  // admin onaylar
  await req(`/api/admin/users/${sid}/approve`, { method: 'POST', token: adminToken });
  const login = await req('/api/auth/login', { method: 'POST', body: { email: 'onay@test.com', password: 'sifre123' } });
  const claimAfter = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: login.data.token });
  assert.strictEqual(claimAfter.status, 201);
  assert.strictEqual(claimAfter.data.donation.remaining, 1);
});

// ---------- Kitap veri tabanı find-or-create ----------
test('aynı ad+yazar kitabı tekrar oluşturulmaz (mevcut döner)', async () => {
  const donorToken = await makeDonor('donor-book@test.com');
  const first = await req('/api/books', { method: 'POST', token: donorToken, body: { title: 'Yabancı', author: 'Camus' } });
  assert.strictEqual(first.status, 201);
  assert.strictEqual(first.data.existed, false);

  const dup = await req('/api/books', { method: 'POST', token: donorToken, body: { title: 'yabanci'.replace('yabanci', 'Yabancı'), author: 'camus' } });
  assert.strictEqual(dup.status, 200);
  assert.strictEqual(dup.data.existed, true);
  assert.strictEqual(dup.data.book.id, first.data.book.id);
});

// ---------- Bağış + teslimat akışı ----------
test('bağış: oluştur, talep et, kargola, teslim al', async () => {
  const donorToken = await makeDonor('donor-flow@test.com');
  const student = await approvedStudent({ name: 'Akış', email: 'akis@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-AKIS', address: 'Teslim Adresi 5' });
  const bookId = await makeBook(donorToken, 'Beyaz Diş', 'Jack London');

  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, source: 'own', target_level: 'lise' } });
  assert.strictEqual(don.status, 201);

  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  assert.strictEqual(claim.status, 201);
  const claimId = claim.data.claim_id;

  // bağışçı eşleşen öğrencinin adresini görebilir
  const mine = await req('/api/donations/mine', { token: donorToken });
  const claimer = mine.data[0].claimers[0];
  assert.strictEqual(claimer.address, 'Teslim Adresi 5');

  const ship = await req(`/api/donations/claims/${claimId}/ship`, { method: 'POST', token: donorToken });
  assert.strictEqual(ship.status, 200);
  const deliver = await req(`/api/donations/claims/${claimId}/deliver`, { method: 'POST', token: student.token });
  assert.strictEqual(deliver.status, 200);
  assert.strictEqual(deliver.data.status, 'delivered');
});

// ---------- Haftalık 3 kitap sınırı ----------
test('haftalık 3 kitap sınırı uygulanır', async () => {
  const donorToken = await makeDonor('donor-limit@test.com');
  const student = await approvedStudent({ name: 'Limit', email: 'limit@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-LIM', address: 'Adres' });

  for (let i = 0; i < 4; i++) {
    const bookId = await makeBook(donorToken, `Limit Kitap ${i}`, 'Yazar');
    const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, target_level: 'lise' } });
    const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
    if (i < 3) assert.strictEqual(claim.status, 201, `talep ${i} başarılı olmalı`);
    else assert.strictEqual(claim.status, 403, '4. kitap haftalık sınır nedeniyle reddedilmeli');
  }
  const quota = await req('/api/me/quota', { token: student.token });
  assert.strictEqual(quota.data.weeklyUsed, 3);
  assert.strictEqual(quota.data.weeklyRemaining, 0);
  assert.strictEqual(quota.data.monthlyLimit, 10);
});

// ---------- İstek akışı ----------
test('istek: öğrenci oluşturur, bağışçı karşılar, adres paylaşılır', async () => {
  const donorToken = await makeDonor('donor-req@test.com');
  const student = await approvedStudent({ name: 'İstekçi', email: 'istek@test.com', password: 'sifre123', school_level: 'ortaokul', document_no: 'BELGE-REQ', address: 'İstek Adresi 9' });
  const bookId = await makeBook(donorToken, 'Matematik 8', null);

  const created = await req('/api/requests', { method: 'POST', token: student.token, body: { book_id: bookId } });
  assert.strictEqual(created.status, 201);
  // açık istek listesi adres sızdırmaz
  const open = await req('/api/requests?status=open');
  assert.ok(open.data.every((r) => !('address' in r)));

  const fulfill = await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donorToken, body: { source: 'purchase' } });
  assert.strictEqual(fulfill.status, 200);
  assert.strictEqual(fulfill.data.status, 'fulfilled');

  // karşılayan bağışçı adresi görür
  const fulfilled = await req('/api/requests/fulfilled/mine', { token: donorToken });
  assert.strictEqual(fulfilled.data[0].address, 'İstek Adresi 9');

  const again = await req(`/api/requests/${created.data.id}/fulfill`, { method: 'POST', token: donorToken });
  assert.strictEqual(again.status, 409);
});

// ---------- Admin yetkileri ----------
test('admin engelleme ve silme yetkileri', async () => {
  const reg = await req('/api/auth/register/donor', { method: 'POST', body: { name: 'Engellenecek', email: 'block@test.com', password: 'sifre123' } });
  const uid = reg.data.user.id;

  const block = await req(`/api/admin/users/${uid}/block`, { method: 'POST', token: adminToken });
  assert.strictEqual(block.status, 200);

  // engellenmiş kullanıcı giriş yapamaz
  const login = await req('/api/auth/login', { method: 'POST', body: { email: 'block@test.com', password: 'sifre123' } });
  assert.strictEqual(login.status, 403);

  await req(`/api/admin/users/${uid}/unblock`, { method: 'POST', token: adminToken });
  const del = await req(`/api/admin/users/${uid}`, { method: 'DELETE', token: adminToken });
  assert.strictEqual(del.status, 200);
});

test('admin olmayan admin uçlarına erişemez', async () => {
  const donorToken = await makeDonor('donor-noadmin@test.com');
  const r = await req('/api/admin/users', { token: donorToken });
  assert.strictEqual(r.status, 403);
});

test('öğrenci bağış oluşturamaz (rol kontrolü)', async () => {
  const student = await approvedStudent({ name: 'Rol', email: 'rol@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-ROL', address: 'Adres' });
  const bookId = await makeBook(adminToken, 'Rol Kitabı', 'X');
  const r = await req('/api/donations', { method: 'POST', token: student.token, body: { book_id: bookId, quantity: 1 } });
  assert.strictEqual(r.status, 403);
});

// ---------- Geçersiz e-posta ----------
test('geçersiz e-posta ile kayıt reddedilir', async () => {
  const r = await req('/api/auth/register/donor', { method: 'POST', body: { name: 'X', email: 'gecersiz', password: 'sifre123' } });
  assert.strictEqual(r.status, 400);
});

// ---------- Bildirimler ----------
test('talep ve onay olayları bildirim üretir', async () => {
  const donorToken = await makeDonor('donor-notif@test.com');

  // kayıt -> onay öğrenciye bildirim gönderir
  const reg = await req('/api/auth/register/student', {
    method: 'POST',
    form: studentForm({ name: 'Bildirim', email: 'notif@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-NOTIF', address: 'Adres' }),
  });
  await req(`/api/admin/users/${reg.data.user.id}/approve`, { method: 'POST', token: adminToken });
  const login = await req('/api/auth/login', { method: 'POST', body: { email: 'notif@test.com', password: 'sifre123' } });
  const sToken = login.data.token;

  let notifs = await req('/api/me/notifications', { token: sToken });
  assert.ok(notifs.data.some((n) => n.type === 'document_approved'), 'onay bildirimi olmalı');

  // talep -> bağışçıya bildirim
  const bookId = await makeBook(donorToken, 'Bildirim Kitabı', 'Y');
  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, target_level: 'lise' } });
  await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: sToken });

  const donorNotifs = await req('/api/me/notifications', { token: donorToken });
  assert.ok(donorNotifs.data.some((n) => n.type === 'donation_claimed'));

  // okunmamış sayısı + okundu işaretleme
  const count = await req('/api/me/notifications/unread-count', { token: donorToken });
  assert.ok(count.data.count >= 1);
  await req('/api/me/notifications/read-all', { method: 'POST', token: donorToken });
  const after = await req('/api/me/notifications/unread-count', { token: donorToken });
  assert.strictEqual(after.data.count, 0);
});

// ---------- Profil yönetimi ----------
test('öğrenci profil/adres ve şifre günceller', async () => {
  const student = await approvedStudent({ name: 'Profil', email: 'profil@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-PROF', address: 'Eski Adres' });

  const upd = await req('/api/me', { method: 'PATCH', token: student.token, body: { address: 'Yeni Adres 42', phone: '5551112233' } });
  assert.strictEqual(upd.status, 200);
  assert.strictEqual(upd.data.address, 'Yeni Adres 42');

  // yanlış mevcut şifre -> 403
  const badPw = await req('/api/me', { method: 'PATCH', token: student.token, body: { current_password: 'yanlis', new_password: 'yenisifre' } });
  assert.strictEqual(badPw.status, 403);

  // doğru mevcut şifre -> yeni şifreyle giriş
  const okPw = await req('/api/me', { method: 'PATCH', token: student.token, body: { current_password: 'sifre123', new_password: 'yenisifre' } });
  assert.strictEqual(okPw.status, 200);
  const relogin = await req('/api/auth/login', { method: 'POST', body: { email: 'profil@test.com', password: 'yenisifre' } });
  assert.strictEqual(relogin.status, 200);
});

// ---------- Talep iptali ----------
test('öğrenci talebini iptal edince adet geri açılır', async () => {
  const donorToken = await makeDonor('donor-cancel@test.com');
  const student = await approvedStudent({ name: 'İptal', email: 'iptal@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-IPTAL', address: 'Adres' });
  const bookId = await makeBook(donorToken, 'İptal Kitabı', 'Z');

  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, target_level: 'lise' } });
  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  assert.strictEqual(claim.data.donation.remaining, 0);

  const cancel = await req(`/api/donations/claims/${claim.data.claim_id}`, { method: 'DELETE', token: student.token });
  assert.strictEqual(cancel.status, 200);

  // bağış yeniden açık ve kullanılabilir
  const list = await req('/api/donations');
  assert.ok(list.data.some((d) => d.id === don.data.id && d.remaining === 1));

  // kota da serbest kalmış olmalı
  const quota = await req('/api/me/quota', { token: student.token });
  assert.strictEqual(quota.data.weeklyUsed, 0);
});

// ---------- Bağışçı kendi bağışını yönetir ----------
test('bağışçı talepsiz bağışını siler, talepli bağışı silemez', async () => {
  const donorToken = await makeDonor('donor-manage@test.com');
  const bookId = await makeBook(donorToken, 'Yönetim Kitabı', 'W');

  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 2 } });
  // talep yokken silinebilir
  const del = await req(`/api/donations/${don.data.id}`, { method: 'DELETE', token: donorToken });
  assert.strictEqual(del.status, 200);

  // talep alınca silinemez, kapatılabilir
  const student = await approvedStudent({ name: 'Y2', email: 'y2@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-Y2', address: 'Adres' });
  const don2 = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 2, target_level: 'lise' } });
  await req(`/api/donations/${don2.data.id}/claim`, { method: 'POST', token: student.token });
  const del2 = await req(`/api/donations/${don2.data.id}`, { method: 'DELETE', token: donorToken });
  assert.strictEqual(del2.status, 409);
  const close = await req(`/api/donations/${don2.data.id}/close`, { method: 'POST', token: donorToken });
  assert.strictEqual(close.status, 200);
});

// ---------- Filtreleme ----------
test('bağış listesi seviye ve metin ile filtrelenir', async () => {
  const donorToken = await makeDonor('donor-filter@test.com');
  const bookId = await makeBook(donorToken, 'Filtre Coğrafya', 'Coğrafyacı');
  await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, target_level: 'universite' } });

  // ortaokul filtresi üniversiteye özel bağışı getirmez
  const orta = await req('/api/donations?level=ortaokul&q=Filtre Coğrafya');
  assert.ok(!orta.data.some((d) => d.book_id === bookId));
  // üniversite filtresi getirir
  const uni = await req('/api/donations?level=universite&q=Coğrafya');
  assert.ok(uni.data.some((d) => d.book_id === bookId));
});

// ---------- Kamuya açık istatistikler ----------
test('genel istatistik uçları erişilebilir', async () => {
  const r = await req('/api/stats');
  assert.strictEqual(r.status, 200);
  assert.ok(typeof r.data.books === 'number');
  assert.ok('booksDelivered' in r.data);
  assert.ok('activeStudents' in r.data);
});

// ---------- Kitap detayı uygunluk bilgisi ----------
test('kitap detayı açık bağış/istek sayılarını içerir', async () => {
  const donorToken = await makeDonor('donor-detail@test.com');
  const bookId = await makeBook(donorToken, 'Detay Kitabı', 'Yazar');
  await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 2 } });

  const detail = await req(`/api/books/${bookId}`);
  assert.strictEqual(detail.status, 200);
  assert.strictEqual(detail.data.availableDonations, 1);
  assert.strictEqual(detail.data.openRequests, 0);
});

// ---------- Admin içerik listeleme ----------
test('admin tüm bağış ve istekleri listeler', async () => {
  const donorToken = await makeDonor('donor-adminlist@test.com');
  const bookId = await makeBook(donorToken, 'Admin Liste Kitabı', 'Yazar');
  await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1 } });

  const dons = await req('/api/admin/donations', { token: adminToken });
  assert.strictEqual(dons.status, 200);
  assert.ok(dons.data.some((d) => d.book_title === 'Admin Liste Kitabı'));

  const reqs = await req('/api/admin/requests', { token: adminToken });
  assert.strictEqual(reqs.status, 200);
  assert.ok(Array.isArray(reqs.data));
});

// ---------- Giriş oran sınırı ----------
test('çok sayıda başarısız girişte oran sınırı devreye girer', async () => {
  await makeDonor('donor-brute@test.com');
  let sawLimit = false;
  for (let i = 0; i < 10; i++) {
    const r = await req('/api/auth/login', { method: 'POST', body: { email: 'donor-brute@test.com', password: 'yanlissifre' } });
    if (r.status === 429) { sawLimit = true; break; }
    assert.strictEqual(r.status, 401);
  }
  assert.ok(sawLimit, '8 başarısız denemeden sonra 429 dönmeli');
});

// ---------- Teşekkür notu ----------
test('teslim sonrası öğrenci bağışçıya teşekkür gönderir', async () => {
  const donorToken = await makeDonor('donor-thanks@test.com');
  const student = await approvedStudent({ name: 'Teşekkür', email: 'tesekkur@test.com', password: 'sifre123', school_level: 'lise', document_no: 'BELGE-THX', address: 'Adres' });
  const bookId = await makeBook(donorToken, 'Teşekkür Kitabı', 'Yazar');

  const don = await req('/api/donations', { method: 'POST', token: donorToken, body: { book_id: bookId, quantity: 1, target_level: 'lise' } });
  const claim = await req(`/api/donations/${don.data.id}/claim`, { method: 'POST', token: student.token });
  const claimId = claim.data.claim_id;

  // teslim alınmadan teşekkür -> 409
  const early = await req(`/api/donations/claims/${claimId}/thank`, { method: 'POST', token: student.token, body: { message: 'Sağ olun' } });
  assert.strictEqual(early.status, 409);

  await req(`/api/donations/claims/${claimId}/ship`, { method: 'POST', token: donorToken });
  await req(`/api/donations/claims/${claimId}/deliver`, { method: 'POST', token: student.token });

  const thank = await req(`/api/donations/claims/${claimId}/thank`, { method: 'POST', token: student.token, body: { message: 'Çok teşekkürler!' } });
  assert.strictEqual(thank.status, 201);

  const donorNotifs = await req('/api/me/notifications', { token: donorToken });
  assert.ok(donorNotifs.data.some((n) => n.type === 'thank_you'));
});
