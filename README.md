# 📚 Kitap Bağış Platformu

Bağışçıların ortaokul, lise ve üniversite öğrencilerine kitap hediye edebileceği bir bağış platformu ("Ismarlıyo" benzeri). İki ana akış vardır:

1. **Bağışçı → Öğrenci:** Bağışçı bağışlamak istediği kitabı ve miktarını yayınlar; uygun öğrenciler bu bağıştan kitap alır.
2. **Öğrenci → Bağışçı:** Öğrenci ihtiyaç duyduğu kitabı istek olarak listeler; bir bağışçı satın alarak isteği karşılar.

## Öne çıkan kurallar

- **Öğrenci belgesi zorunlu:** Öğrenci kaydı için belge numarası ve belge dosyası (PDF/görsel) yüklenmesi gerekir.
- **Tek belge, tek kayıt:** Aynı öğrenci belge numarası ile ikinci bir hesap açılamaz (veritabanında benzersizlik kısıtı).
- **Öğrenci bağış sınırı:** Her öğrenci **haftada en fazla 3**, **3 ayda (90 gün) en fazla 10** kitap alabilir. Hem bağıştan alınan kitaplar hem de karşılanan istekler bu sınıra dahildir.
- **Bağışçı sınırı yoktur.**

## Teknoloji

- **Backend:** Node.js + Express
- **Veritabanı:** SQLite (`better-sqlite3`)
- **Kimlik doğrulama:** JWT + bcrypt
- **Dosya yükleme:** multer (öğrenci belgesi)
- **Arayüz:** statik HTML/CSS/JS (derleme adımı yok)

## Kurulum ve çalıştırma

```bash
npm install
npm start          # http://localhost:3000
# geliştirme için:
npm run dev        # dosya değişikliklerinde otomatik yeniden başlatma
```

### Ortam değişkenleri

| Değişken      | Açıklama                              | Varsayılan           |
| ------------- | ------------------------------------- | -------------------- |
| `PORT`        | Sunucu portu                          | `3000`               |
| `JWT_SECRET`  | JWT imzalama anahtarı (üretimde değiştirin) | dev anahtarı   |
| `DB_PATH`     | SQLite veritabanı dosya yolu          | `data/kitap.db`      |

## Testler

```bash
npm test
```

`node:test` ile yazılmış uçtan uca API testleri; kayıt, tek belge kısıtı, bağış/talep akışı, istek karşılama ve haftalık bağış sınırını kapsar.

## API özeti

### Kimlik

| Yöntem | Yol                          | Açıklama                                  |
| ------ | ---------------------------- | ----------------------------------------- |
| POST   | `/api/auth/register/donor`   | Bağışçı kaydı                             |
| POST   | `/api/auth/register/student` | Öğrenci kaydı (`multipart/form-data`, `document` dosyası) |
| POST   | `/api/auth/login`            | Giriş                                     |
| GET    | `/api/me/quota`              | Öğrencinin kalan bağış hakkı              |

### Bağışlar (1. akış)

| Yöntem | Yol                              | Rol     | Açıklama                          |
| ------ | -------------------------------- | ------- | -------------------------------- |
| POST   | `/api/donations`                 | donor   | Yeni kitap bağışı oluştur        |
| GET    | `/api/donations`                 | herkes  | Kitap alınabilecek açık bağışlar |
| GET    | `/api/donations/mine`            | donor   | Bağışçının kendi bağışları       |
| POST   | `/api/donations/:id/claim`       | student | Bağıştan kitap al (kota kontrolü)|
| GET    | `/api/donations/claimed/mine`    | student | Öğrencinin aldığı kitaplar       |

### İstekler (2. akış)

| Yöntem | Yol                          | Rol     | Açıklama                            |
| ------ | ---------------------------- | ------- | ----------------------------------- |
| POST   | `/api/requests`              | student | Kitap isteği oluştur                |
| GET    | `/api/requests`              | herkes  | Açık istekler (`?status=all` hepsi) |
| GET    | `/api/requests/mine`         | student | Öğrencinin kendi istekleri          |
| DELETE | `/api/requests/:id`          | student | Açık isteği iptal et                |
| POST   | `/api/requests/:id/fulfill`  | donor   | İsteği satın alarak karşıla (kota kontrolü) |

## Proje yapısı

```
kitap/
├── server.js              # Express uygulaması
├── src/
│   ├── db.js              # SQLite şeması ve bağlantı
│   ├── auth.js            # JWT yardımcıları ve middleware
│   ├── limits.js          # Öğrenci bağış kotası mantığı
│   └── routes/
│       ├── auth.js        # kayıt / giriş
│       ├── donations.js   # bağış oluşturma ve talep
│       └── requests.js    # istek oluşturma ve karşılama
├── public/                # statik arayüz (HTML/CSS/JS)
└── test/api.test.js       # uçtan uca testler
```
