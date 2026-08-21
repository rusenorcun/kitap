# 📚 Kitap Bağış Platformu — Backend API

Öğrencilere öncelik veren, herkesin katılabildiği bir kitap bağış ve takas platformunun **backend (REST API)** bölümü. Mobil uygulama ve web sitesi bu API'yi tüketecek şekilde ayrıca geliştirilecektir.

## Hesap modeli

Tek hesap; herkes hem **bağış yapabilir** hem de **kitap alabilir**. Alıcı iki katmandan biridir:

- **Üye** — belgesiz. Bağış yapar, takas eder, kitap alır. Öncelik yok, kotası düşüktür.
- **Öğrenci** — belgesi admin tarafından onaylanmış üye. Bağışta **48 saat öncelik** ve daha yüksek kota kazanır.

Üye, istediği zaman `POST /api/me/verify-student` ile öğrenci doğrulamasına başvurabilir (belge admin onayına gider). **admin** ise `is_admin` bayrağıyla tanımlanır.

## Akışlar

1. **Bağış:** Bir üye kitap veri tabanından kitap seçip miktar, hedef seviye ve kaynak (`purchase` = satın alıp gönderir / `own` = elindeki kopya) belirler. Uygun alıcılar alır.
2. **İstek:** Bir alıcı ihtiyaç duyduğu kitabı istek olarak listeler; başka biri satın alarak ya da elindeki kopyayla karşılar.
3. **Takas:** Üyeler ellerindeki kitapları takasa açar, başkasının kitabına kendi kitabıyla teklif verir; karşı taraf kabul edince iki taraf adres paylaşıp karşılıklı kargolar. Kotadan bağımsızdır.

Her akışta kitap, eşleşen tarafın **teslimat adresine kargolanır**. Adres yalnızca eşleşilen karşı tarafa gösterilir. Teslimat `matched → shipped → delivered` (takas: çift kargo → `completed`) olarak takip edilir.

## Kurallar

- **Öğrenci önceliği:** Yeni bağış ilk **48 saat** yalnızca onaylı öğrencilere açıktır; süre dolunca tüm üyelere açılır.
- **Bağış alma sınırı (kota):** Öğrenci son 7 günde **3**, 30 günde **10**; üye son 7 günde **1**, 30 günde **3**. Bağıştan alınanlar ve karşılanan istekler sayıma dahildir. Bağış yapmanın sınırı yoktur.
- **Teslimat adresi:** Kitap almak / istek oluşturmak / takas için profilde adres tanımlı olmalıdır (`ADDRESS_REQUIRED`).
- **Öğrenci belgesi:** Öğrenci doğrulaması belge no + belge dosyası (PDF/görsel) gerektirir. Aynı belge numarası ile iki kayıt olmaz (DB benzersizlik). Belge admin onayına kadar `pending`'dir.
- **Kitap veri tabanı:** Bağış/istek/takas buradan kitap seçer. Aynı **ad + yazar** ikinci kez oluşturulamaz (find-or-create). Alışveriş linki verilirse başlık ve kapak **OpenGraph** (og:title / og:image) ile otomatik doldurulur; yazar elle onaylanır.
- **admin** — tüm yetkiler: öğrenci belge onay/ret, engelleme, silme, admin atama, içerik (kitap/bağış/istek/takas) denetimi, belge görüntüleme, istatistik.

## Kurulum ve çalıştırma

```bash
npm install
ADMIN_EMAIL=admin@ornek.com ADMIN_PASSWORD=guclu-parola npm start   # http://localhost:3000
npm run dev    # geliştirme (otomatik yeniden başlatma)
npm test       # uçtan uca testler
```

### Ortam değişkenleri

| Değişken          | Açıklama                                            | Varsayılan        |
| ----------------- | --------------------------------------------------- | ----------------- |
| `PORT`            | Sunucu portu                                        | `3000`            |
| `JWT_SECRET`      | JWT imzalama anahtarı (üretimde mutlaka değiştirin) | dev anahtarı      |
| `DB_PATH`         | SQLite veritabanı dosya yolu                        | `data/kitap.db`   |
| `ADMIN_EMAIL`     | Açılışta oluşturulacak/garantilenecek ilk admin     | —                 |
| `ADMIN_PASSWORD`  | İlk admin parolası                                  | —                 |
| `ADMIN_NAME`      | İlk admin adı                                       | `Yönetici`        |
| `HTTPS_PROXY`     | Ayarlıysa OpenGraph isteklerinde proxy kullanılır   | —                 |

> `ADMIN_EMAIL`/`ADMIN_PASSWORD` verilirse her açılışta o admin oluşturulur; e-posta zaten varsa o kullanıcı admin'e yükseltilir.

## API özeti

Kimlik doğrulama: `Authorization: Bearer <token>`. Token, kayıt/giriş yanıtında döner.

### Kimlik (`/api/auth`)

| Yöntem | Yol         | Açıklama                                                                                          |
| ------ | ----------- | ------------------------------------------------------------------------------------------------ |
| POST   | `/register` | Üye kaydı. `document` + `school_level` + `document_no` gönderilirse öğrenci doğrulaması `pending` başlar (`multipart/form-data`) |
| POST   | `/login`    | Giriş (engelli kullanıcı giremez; başarısız denemelere oran sınırı)                             |

### Kitaplar (`/api/books`)

| Yöntem | Yol                  | Erişim          | Açıklama                                              |
| ------ | -------------------- | --------------- | ---------------------------------------------------- |
| POST   | `/preview`           | giriş yapan     | `{url}` linkinden başlık/kapak önizleme              |
| POST   | `/`                  | giriş yapan     | Kitap oluştur/bul (link veya elle; `cover` yüklenebilir) |
| GET    | `/`                  | herkes          | Kitap arama/listeleme (`?q=`)                        |
| GET    | `/:id`               | herkes          | Kitap detayı                                         |
| GET    | `/cover/:filename`   | herkes          | Yüklenen kapak görseli                               |
| PATCH  | `/:id`               | admin           | Kitap düzelt                                         |

### Bağışlar (`/api/donations`)

| Yöntem | Yol                          | Erişim          | Açıklama                                  |
| ------ | ---------------------------- | --------------- | ----------------------------------------- |
| POST   | `/`                          | üye             | Bağış oluştur (`book_id`, `quantity`, `source`, `target_level`) |
| GET    | `/`                          | herkes          | Açık bağışlar (`priority_active`, `priority_until` alanlarıyla) |
| GET    | `/mine`                      | üye             | Bağışlarım + alanların adresi             |
| POST   | `/:id/claim`                 | alıcı (adresli) | Bağıştan kitap al (öncelik penceresi + kota) |
| POST   | `/:id/close`                 | bağış sahibi    | Kendi bağışını kapat                      |
| DELETE | `/:id`                       | bağış sahibi    | Kendi bağışını sil (talep yoksa)          |
| GET    | `/claimed/mine`              | alıcı           | Aldığım kitaplar                          |
| POST   | `/claims/:claimId/ship`      | bağış sahibi    | Kargoya verildi                           |
| POST   | `/claims/:claimId/deliver`   | alıcı           | Teslim alındı                             |
| POST   | `/claims/:claimId/thank`     | alıcı           | Bağışçıya teşekkür notu (teslim sonrası)  |
| DELETE | `/claims/:claimId`           | alıcı           | Talebi iptal et (kargolanmadan; adet geri açılır) |

> Listeleme filtreleri: `GET /api/donations?level=lise&book_id=3&q=metin`

### İstekler (`/api/requests`)

| Yöntem | Yol                | Erişim          | Açıklama                                       |
| ------ | ------------------ | --------------- | ---------------------------------------------- |
| POST   | `/`                | alıcı (adresli) | İstek oluştur (`book_id`)                      |
| GET    | `/`                | herkes          | Açık istekler (adres gizli; `?status=all`)     |
| GET    | `/mine`            | alıcı           | Kendi isteklerim                               |
| GET    | `/fulfilled/mine`  | karşılayan      | Karşıladığım istekler (alıcı adresli)          |
| DELETE | `/:id`             | istek sahibi    | Açık isteği sil                                |
| POST   | `/:id/fulfill`     | üye             | İsteği karşıla (`source`; alıcının kotası kontrol edilir) |
| POST   | `/:id/ship`        | karşılayan      | Kargoya verildi                                |
| POST   | `/:id/deliver`     | istek sahibi    | Teslim alındı                                  |

> Listeleme filtreleri: `GET /api/requests?status=open&level=lise&book_id=3&q=metin`

### Takas (`/api/swaps`)

| Yöntem | Yol                       | Erişim          | Açıklama                                             |
| ------ | ------------------------- | --------------- | --------------------------------------------------- |
| GET    | `/books`                  | giriş yapan     | Başkalarının takasa açık kitapları (`?q=`)          |
| GET    | `/books/mine`             | giriş yapan     | Kendi takas kitaplarım                              |
| POST   | `/books`                  | giriş yapan     | Kitabımı takasa aç (`book_id`, `note`)              |
| PATCH  | `/books/:id`              | sahibi          | Aç/kapat (`status`)                                 |
| DELETE | `/books/:id`              | sahibi          | Takastan kaldır                                     |
| POST   | `/offers`                 | teklif eden     | Teklif ver (`target_swap_book_id`, `offered_swap_book_id`) |
| GET    | `/offers/incoming`        | giriş yapan     | Bana gelen teklifler                                |
| GET    | `/offers/outgoing`        | giriş yapan     | Gönderdiğim teklifler                               |
| POST   | `/offers/:id/accept`      | hedef sahibi    | Kabul et (kitaplar kapanır, adresler paylaşılır)    |
| POST   | `/offers/:id/reject`      | hedef sahibi    | Reddet                                              |
| POST   | `/offers/:id/cancel`      | teklif eden     | Geri çek                                            |
| POST   | `/offers/:id/ship`        | iki taraf       | Kargoya verdim (ikisi de verince `completed`)       |

> Kabul edilmiş/tamamlanmış takasta `from_address` / `to_address` iki tarafa gösterilir; öncesinde gizlidir.

### Bana özel (`/api/me`)

| Yöntem | Yol                              | Erişim       | Açıklama                                      |
| ------ | -------------------------------- | ------------ | --------------------------------------------- |
| GET    | `/`                              | giriş yapan  | Profil bilgisi (`isStudent`, `recipientTier`) |
| PATCH  | `/`                              | giriş yapan  | Profil güncelle (ad, adres/telefon, şifre)    |
| POST   | `/verify-student`                | giriş yapan  | Öğrenci doğrulaması başlat (`multipart`: `document` + `school_level` + `document_no`) |
| GET    | `/quota`                         | giriş yapan  | Kalan bağış hakkı (tier'a göre haftalık/aylık) |
| GET    | `/notifications`                 | giriş yapan  | Bildirimler (`?unread=true`)                  |
| GET    | `/notifications/unread-count`    | giriş yapan  | Okunmamış bildirim sayısı                     |
| POST   | `/notifications/:id/read`        | giriş yapan  | Bildirimi okundu işaretle                     |
| POST   | `/notifications/read-all`        | giriş yapan  | Tümünü okundu işaretle                        |

> Bildirim olayları: belge onay/ret, bağış talep edildi, istek karşılandı, kargolandı, teslim alındı, talep iptali, teşekkür, takas teklifi/kabul/ret/kargo/tamamlandı.

### Admin

| Yöntem | Yol                            | Erişim  | Açıklama                                     |
| ------ | ------------------------------ | ------- | -------------------------------------------- |
| GET    | `/api/admin/stats`             | admin   | İstatistikler                                |
| GET    | `/api/admin/donations`         | admin   | Tüm bağışları listele (denetim)              |
| GET    | `/api/admin/requests`          | admin   | Tüm istekleri listele (denetim)              |
| GET    | `/api/admin/users`             | admin   | Kullanıcılar (`?student_status= ?blocked= ?admin=`) |
| GET    | `/api/admin/users/:id`         | admin   | Kullanıcı detayı                             |
| GET    | `/api/admin/users/:id/document`| admin   | Öğrenci belgesini görüntüle                  |
| POST   | `/api/admin/users/:id/approve` | admin   | Belge onayla                                 |
| POST   | `/api/admin/users/:id/reject`  | admin   | Belge reddet                                 |
| POST   | `/api/admin/users/:id/block`   | admin   | Engelle                                      |
| POST   | `/api/admin/users/:id/unblock` | admin   | Engeli kaldır                                |
| POST   | `/api/admin/users/:id/promote` | admin   | Admin'e yükselt                              |
| POST   | `/api/admin/admins`            | admin   | Yeni admin oluştur                           |
| DELETE | `/api/admin/users/:id`         | admin   | Kullanıcı sil                                |
| DELETE | `/api/admin/donations/:id`     | admin   | Bağış sil                                    |
| DELETE | `/api/admin/requests/:id`      | admin   | İstek sil                                    |
| DELETE | `/api/admin/books/:id`         | admin   | Kitap sil (kullanılmıyorsa)                  |

### Genel (kimlik gerektirmez)

| Yöntem | Yol           | Açıklama                                                    |
| ------ | ------------- | ---------------------------------------------------------- |
| GET    | `/api/stats`  | Ana sayfa için özet istatistikler (kişisel veri içermez)   |
| GET    | `/api/health` | Sağlık kontrolü                                            |

> **Güvenlik:** Giriş uçları başarısız denemelere karşı oran sınırlıdır (ip + e-posta başına 15 dakikada 8 başarısız deneme); başarılı girişte sayaç sıfırlanır.

## Teknoloji

- **Node.js + Express** (saf JSON API)
- **SQLite** (`better-sqlite3`)
- **JWT + bcrypt** kimlik doğrulama
- **multer** dosya yükleme (öğrenci belgesi, kitap kapağı)
- **OpenGraph** üst veri çekme (`fetch` + regex, harici bağımlılık yok)

## Proje yapısı

```
kitap/
├── server.js              # Express uygulaması (saf API)
├── src/
│   ├── db.js              # SQLite şeması (users, books, donations, claims, requests, swap_books, swap_offers, notifications)
│   ├── auth.js            # JWT + rol/onay/engel middleware'leri
│   ├── limits.js          # Tier kotası (üye/öğrenci) + öncelik penceresi
│   ├── og.js              # OpenGraph üst veri ayrıştırıcı + getirici
│   ├── notifications.js   # Bildirim yardımcıları
│   ├── validate.js        # Girdi doğrulama (e-posta vb.)
│   ├── ratelimit.js       # Bellek-içi oran sınırlayıcı (giriş koruması)
│   ├── seed.js            # Ortam değişkeninden ilk admin
│   └── routes/
│       ├── auth.js        # kayıt / giriş (oran sınırlı)
│       ├── books.js       # kitap veri tabanı (find-or-create, link/kapak)
│       ├── donations.js   # bağışlar + talep + teslimat + öncelik + teşekkür
│       ├── requests.js    # istekler + karşılama + teslimat
│       ├── swaps.js       # kitap takası (takas kitapları + teklifler)
│       ├── me.js          # profil, öğrenci doğrulama, kota, bildirimler
│       ├── public.js      # kamuya açık istatistikler
│       └── admin.js       # yönetici işlemleri
└── test/api.test.js       # uçtan uca testler
```
