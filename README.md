# 📚 Kitap Bağış Platformu — Backend API

Bağışçıların ortaokul, lise ve üniversite öğrencilerine kitap hediye edebileceği bir bağış platformunun **backend (REST API)** bölümü. Mobil uygulama ve web sitesi bu API'yi tüketecek şekilde ayrıca geliştirilecektir.

## Akışlar

1. **Bağışçı → Öğrenci:** Bağışçı kitap veri tabanından bir kitap seçer (yoksa oluşturur), miktarını ve hedef seviyeyi belirler. Kaynak `purchase` (satın alıp gönderir) ya da `own` (elindeki kopyayı gönderir) olabilir. Uygun öğrenciler bu bağıştan kitap alır.
2. **Öğrenci → Bağışçı:** Öğrenci ihtiyaç duyduğu kitabı (yine veri tabanından) istek olarak listeler; bir bağışçı satın alarak ya da elindeki kopyayla karşılar.

Her iki akışta da kitap, eşleşen tarafın **teslimat adresine kargolanır**. Adres yalnızca eşleşilen karşı tarafa gösterilir. Teslimat `matched → shipped → delivered` olarak takip edilir.

## Kurallar

- **Öğrenci belgesi zorunlu:** Kayıtta belge numarası + belge dosyası (PDF/görsel) ve teslimat adresi gerekir.
- **Tek belge, tek kayıt:** Aynı belge numarası ile ikinci hesap açılamaz (DB benzersizlik kısıtı).
- **Belge onayı:** Öğrenci `pending` başlar; **admin onaylayana kadar** kitap alamaz / istek oluşturamaz.
- **Öğrenci bağış sınırı:** Son **7 günde en fazla 3**, son **30 günde en fazla 10** kitap. Bağıştan alınanlar ve karşılanan istekler bu sayıma dahildir.
- **Bağışçı için sınır yoktur.**
- **Kitap veri tabanı:** Bağış/istekler buradan kitap seçer. Aynı **ad + yazar** ikinci kez oluşturulamaz (find-or-create). Kitap, alışveriş linkiyle eklenirse başlık ve kapak **OpenGraph** (og:title / og:image) üzerinden otomatik doldurulur; yazar elle girilir/onaylanır.

## Roller

- **donor** — kitap bağışlar, öğrenci isteklerini karşılar. Sınırsız.
- **student** — kitap alır ve istek oluşturur. Belge + adres + admin onayı gerektirir.
- **admin** — tüm yetkiler: belge onay/ret, engelleme, silme, admin atama, içerik (kitap/bağış/istek) denetimi, belge görüntüleme, istatistik.

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

| Yöntem | Yol                  | Açıklama                                                            |
| ------ | -------------------- | ------------------------------------------------------------------ |
| POST   | `/register/donor`    | Bağışçı kaydı                                                       |
| POST   | `/register/student`  | Öğrenci kaydı (`multipart/form-data`: `document` dosyası + `address`) |
| POST   | `/login`             | Giriş (engelli kullanıcı giremez)                                  |

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
| POST   | `/`                          | donor           | Bağış oluştur (`book_id`, `quantity`, `source`, `target_level`) |
| GET    | `/`                          | herkes          | Açık bağışlar                             |
| GET    | `/mine`                      | donor           | Bağışlarım + alan öğrenciler (adresli)    |
| POST   | `/:id/claim`                 | onaylı öğrenci  | Bağıştan kitap al (kota kontrolü)         |
| POST   | `/:id/close`                 | donor           | Kendi bağışını kapat                      |
| DELETE | `/:id`                       | donor           | Kendi bağışını sil (talep yoksa)          |
| GET    | `/claimed/mine`              | student         | Aldığım kitaplar                          |
| POST   | `/claims/:claimId/ship`      | donor           | Kargoya verildi                           |
| POST   | `/claims/:claimId/deliver`   | student         | Teslim alındı                             |
| POST   | `/claims/:claimId/thank`     | student         | Bağışçıya teşekkür notu (teslim sonrası)  |
| DELETE | `/claims/:claimId`           | student         | Talebi iptal et (kargolanmadan; adet geri açılır) |

> Listeleme filtreleri: `GET /api/donations?level=lise&book_id=3&q=metin`

### İstekler (`/api/requests`)

| Yöntem | Yol                | Erişim          | Açıklama                                       |
| ------ | ------------------ | --------------- | ---------------------------------------------- |
| POST   | `/`                | onaylı öğrenci  | İstek oluştur (`book_id`)                      |
| GET    | `/`                | herkes          | Açık istekler (adres gizli; `?status=all`)     |
| GET    | `/mine`            | student         | Kendi isteklerim                               |
| GET    | `/fulfilled/mine`  | donor           | Karşıladığım istekler (öğrenci adresli)        |
| DELETE | `/:id`             | student         | Açık isteği sil                                |
| POST   | `/:id/fulfill`     | donor           | İsteği karşıla (`source`; kota kontrolü)       |
| POST   | `/:id/ship`        | donor           | Kargoya verildi                                |
| POST   | `/:id/deliver`     | student         | Teslim alındı                                  |

> Listeleme filtreleri: `GET /api/requests?status=open&level=lise&book_id=3&q=metin`

### Bana özel (`/api/me`)

| Yöntem | Yol                              | Erişim       | Açıklama                                      |
| ------ | -------------------------------- | ------------ | --------------------------------------------- |
| GET    | `/`                              | giriş yapan  | Profil bilgisi                                |
| PATCH  | `/`                              | giriş yapan  | Profil güncelle (ad, adres/telefon, şifre)    |
| GET    | `/quota`                         | student      | Kalan bağış hakkı (haftalık/aylık)            |
| GET    | `/notifications`                 | giriş yapan  | Bildirimler (`?unread=true`)                  |
| GET    | `/notifications/unread-count`    | giriş yapan  | Okunmamış bildirim sayısı                     |
| POST   | `/notifications/:id/read`        | giriş yapan  | Bildirimi okundu işaretle                     |
| POST   | `/notifications/read-all`        | giriş yapan  | Tümünü okundu işaretle                        |

> Bildirim olayları: belge onay/ret, bağış talep edildi, istek karşılandı, kargolandı, teslim alındı, talep iptali.

### Admin

| Yöntem | Yol                            | Erişim  | Açıklama                                     |
| ------ | ------------------------------ | ------- | -------------------------------------------- |
| GET    | `/api/admin/stats`             | admin   | İstatistikler                                |
| GET    | `/api/admin/donations`         | admin   | Tüm bağışları listele (denetim)              |
| GET    | `/api/admin/requests`          | admin   | Tüm istekleri listele (denetim)              |
| GET    | `/api/admin/users`             | admin   | Kullanıcılar (`?role= ?status= ?blocked=`)   |
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
│   ├── db.js              # SQLite şeması (users, books, donations, claims, requests)
│   ├── auth.js            # JWT + rol/onay/engel middleware'leri
│   ├── limits.js          # Öğrenci kota mantığı (7 gün / 30 gün)
│   ├── og.js              # OpenGraph üst veri ayrıştırıcı + getirici
│   ├── notifications.js   # Bildirim yardımcıları
│   ├── validate.js        # Girdi doğrulama (e-posta vb.)
│   ├── ratelimit.js       # Bellek-içi oran sınırlayıcı (giriş koruması)
│   ├── seed.js            # Ortam değişkeninden ilk admin
│   └── routes/
│       ├── auth.js        # kayıt / giriş (oran sınırlı)
│       ├── books.js       # kitap veri tabanı (find-or-create, link/kapak)
│       ├── donations.js   # bağışlar + talep + teslimat + yönetim + teşekkür
│       ├── requests.js    # istekler + karşılama + teslimat
│       ├── me.js          # profil, kota, bildirimler
│       ├── public.js      # kamuya açık istatistikler
│       └── admin.js       # yönetici işlemleri
└── test/api.test.js       # uçtan uca testler
```
