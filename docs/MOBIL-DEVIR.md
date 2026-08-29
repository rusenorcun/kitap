# Mobil uygulama uyarlaması — backend devir notu

Bu belge, KİTAPLA mobil uygulamasını **güncel Spring Boot backend'ine** uyarlayacak
kişi/oturum içindir. Backend'in bugünkü hâli, mobil tarafın nelere dayanabileceği
ve eski sürüme göre nelerin değiştiği burada.

Analiz edilen sürüm: `a9e2336` · 128 dosya · 1232 düğüm · 249 test yeşil.

---

## 1. ÖNCE OKUNMASI GEREKEN: ortada bir API yok

**Backend'de mobil uygulamanın tüketebileceği bir JSON API bulunmuyor.**

97 uç noktanın tamamı **sunucuda render edilen HTML** döndürüyor (Thymeleaf).
Yalnızca iki istisna var ve ikisi de mobil için işe yaramaz:

| Uç | Döndürdüğü |
| --- | --- |
| `GET /saglik` | Düz metin `iyi` (Docker sağlık kontrolü için) |
| `GET /mesajlar/{id}/akis` | SSE olay akışı (yalnızca sinyal, içerik taşımaz) |
| `GET /admin/belge/{id}` | Dosya (yönetici belgesi) |

Bunun sebebi mimari bir tercih: proje Faz 0'da **Thymeleaf + HTMX** ile,
tek parça sunucu-render mimarisi olarak kuruldu. Ayrı bir frontend katmanı yok;
`templates/` klasörü frontend'in kendisi.

### Bunun mobil için anlamı

Mobil uygulama **doğrudan bağlanamaz**. Üç seçenekten biri gerekiyor:

1. **REST API katmanı yazmak (önerilen).** Mevcut servis katmanı bunun için hazır:
   iş kurallarının tamamı `service/` içinde, controller'lar yalnızca ince bir kabuk.
   Aynı servisleri çağıran `@RestController`'lar eklemek doğrudan iş görür.
   Domain mantığına dokunmadan yapılabilir.
2. **WebView sarmalayıcı.** Hızlı ama mobil deneyimi zayıf; bildirim, kamera,
   çevrimdışı gibi yerel yetenekler kaybolur.
3. **Karma.** Kritik ekranlar yerel, gerisi WebView.

Aşağıdaki bölümler 1. seçeneği hedefler.

---

## 2. Eski sürüme göre NE DEĞİŞTİ

Mobil uygulama, projenin **Node.js + REST API** sürümüne göre tasarlanmıştı.
O sürüm depodan kaldırıldı (geçmişte duruyor). Ürün mantığı da o günden bu yana
epey değişti. **Mobil tarafta en çok iş çıkaracak değişiklikler bunlar:**

### 2.1 Kargo kalktı, kampüs içi yüz yüze teslim geldi ⚠️ en büyük değişiklik

| Eski | Yeni |
| --- | --- |
| Kitap kargoyla gönderilirdi | **Kampüs içinde elden teslim** |
| Profilde **teslimat adresi zorunluydu** | **Adres hiç istenmiyor** |
| Adres eşleşilen tarafa gösterilirdi | **Ev adresi hiçbir aşamada paylaşılmıyor** |
| Akış: `eşleşti → kargoda → teslim` | Akış: `eşleşti → buluşma ayarlandı → teslim` |
| — | **Teslim noktası** kavramı (yönetimin tanımladığı kampüs noktaları) |
| — | **Gelinmedi** durumu ve cezası |

Mobil tarafta silinecek/değişecek ekranlar: adres girişi, kargo takibi,
"kargoya verdim" aksiyonu. Yerine gelecekler: teslim noktası seçici, buluşma
ayarlama (yer + saat), buluşma kartı, "gelmedi" bildirimi.

> Kargo ve satın alma **silinmedi, bayrakla kapatıldı**
> (`kitapla.features.shipping`, `.purchase`, `.address`). İleride açılırsa
> eski akış geri gelir. Mobil tarafta bu bayrakları API'den okuyup ekranı
> ona göre kurmak mantıklı olur.

### 2.2 Yeni: mesajlaşma
Eşleşen iki taraf yazışabiliyor (bağış talebi, karşılanan istek, kabul edilmiş
takas üzerinden). Serbest DM yok. Mobil için tamamen yeni bir ekran grubu.

### 2.3 Yeni: şikâyet ve moderasyon
Sohbet/ilan/üye şikâyet edilebiliyor. Mobil için şikâyet formu gerekiyor.

### 2.4 Yeni: buluşma hatırlatması
Buluşmadan 3 saat önce bildirim düşüyor. **Mobil push bildirimi için doğal aday** —
şu an yalnızca uygulama içi bildirim var.

### 2.5 Yeni: şifre sıfırlama
E-posta ile token'lı sıfırlama var (posta gönderimi kapalıyken loglanıyor).

---

## 3. Backend mimarisi

```
web/         Controller (ince kabuk — HTML döndürür, iş kuralı içermez)
service/     İŞ KURALLARI BURADA — REST API bu katmanı çağırmalı
repo/        Spring Data JPA
domain/      JPA varlıkları ve enum'lar
security/    Kimlik doğrulama, oturum tazeleme, giriş sınırı
config/      Güvenlik, özellik bayrakları, açılış verisi
```

**Kritik nokta:** iş kuralları controller'da değil serviste. Bu yüzden REST
katmanı eklemek düşük riskli: yeni controller'lar aynı servisleri çağırır,
kurallar tek yerde kalır, mevcut 249 test geçerliliğini korur.

`spring.jpa.open-in-view=false` — şablonların/DTO'ların eriştiği ilişkiler
repository sorgularında `join fetch` ile çekiliyor. **JSON serileştirirken
dikkat:** tembel ilişkiye dokunursan `LazyInitializationException` alırsın.
DTO'ya map'lemek en güvenlisi (ayrıca `User` entity'sini olduğu gibi
döndürmek `passwordHash`, `documentNo` gibi alanları sızdırır).

---

## 4. Uç nokta envanteri

Aşağıdakiler bugünkü **HTML** uçları. REST karşılıkları yazılırken kaynak
olarak kullanılmalı: her satırın arkasındaki servis çağrısı ve kuralı da yazdım.

### 4.1 Kimlik / hesap

| Yöntem | Yol | Servis | Not |
| --- | --- | --- | --- |
| POST | `/login` | Spring Security | Form: `email`, `password`. **Oturum çerezi** döner |
| POST | `/register` | `UserService.register` | multipart (öğrenci belgesi olabilir) |
| POST | `/sifremi-unuttum` | `PasswordResetService.request` | Adres kayıtlı olmasa da aynı cevap |
| POST | `/sifre-sifirla` | `PasswordResetService.reset` | Token tek kullanımlık, 1 saat |
| GET | `/profil` | — | Profil + kota |
| POST | `/profil` | `UserService.updateProfile` | ad, adres, telefon |
| POST | `/profil/sifre` | `UserService.changePassword` | |
| POST | `/profil/ogrenci` | `UserService.applyForStudent` | multipart belge → PENDING |

### 4.2 Keşif ve bağış

| Yöntem | Yol | Servis |
| --- | --- | --- |
| GET | `/kesfet` | `DonationService.openDonations(Filter)` — `level`, `q`, `available` |
| GET | `/kitap/{id}` | `DonationService.view` + `eligibility` |
| POST | `/kitap/{id}/al` | `DonationService.claim` |
| POST | `/bagis/yeni` | `DonationService.create` (+ `pointId`, `pointNote`) |
| GET | `/bagislarim` | `DonationService.myDonations` |
| POST | `/bagis/{id}/kapat` `/ac` `/sil` | `close` / `reopen` / `delete` |
| GET | `/aldiklarim` | `ClaimRepository.findByStudentWithDetails` |
| POST | `/teslimat/{claimId}/teslim-aldim` | `DonationService.deliver` |
| POST | `/teslimat/{claimId}/tesekkur` | `DonationService.thank` |
| POST | `/teslimat/{claimId}/iptal` | `DonationService.cancelClaim` |

### 4.3 Buluşma (YENİ)

| Yöntem | Yol | Servis |
| --- | --- | --- |
| POST | `/bulusma/bagis/{claimId}` | `DonationService.arrange` |
| POST | `/bulusma/istek/{requestId}` | `RequestService.arrange` |
| POST | `/bulusma/takas/{offerId}` | `SwapService.arrange` |
| POST | `/gelmedi/bagis/{claimId}` | `DonationService.noShow` |
| POST | `/gelmedi/istek/{requestId}` | `RequestService.noShow` |
| POST | `/gelmedi/takas/{offerId}` | `SwapService.noShow` |

Buluşma gövdesi: `pointId` (opsiyonel), `note` (opsiyonel), `at` (ISO tarih-saat).
**En az biri** (`pointId` ya da `note`) dolu olmalı.

### 4.4 İstek

`GET /istekler`, `POST /istek/yeni`, `GET /isteklerim`, `GET /karsiladiklarim`,
`POST /istek/{id}/karsila|teslim-aldim|tesekkur|sil`
→ `RequestService`

### 4.5 Takas

`GET /takas`, `GET|POST /takas/kitaplarim`, `POST /takas/kitaplarim/{id}/durum|sil`,
`GET|POST /takas/teklif/{targetId}`, `GET /takas/takaslarim`,
`POST /takas/teklif/{id}/kabul|reddet|geri-cek|kargola`
→ `SwapService`

> `.../kargola` ucu yüz yüze modda **"kitabı teslim ettim"** anlamına gelir
> (iki taraf da onaylayınca takas tamamlanır). İsim eski akıştan kalma.

### 4.6 Mesajlaşma (YENİ)

| Yöntem | Yol | Servis |
| --- | --- | --- |
| GET | `/mesajlar` | `MessageService.mine` |
| GET | `/mesajlar/ac/{kind}/{refId}` | `MessageService.open` — kind: `claim|request|swap` |
| GET | `/mesajlar/{id}` | `require` + `messagesOf` |
| POST | `/mesajlar/{id}` | `MessageService.send` — gövde: `body` |
| GET | `/mesajlar/{id}/akis` | **SSE** — `yeni` olayı, sinyal taşır |

Mobilde SSE yerine WebSocket ya da push tercih edilebilir; `SseHub` yerine
aynı noktadan tetiklenecek bir yayın mekanizması yazılabilir
(`MessageService.send` içinde `sse.publish(...)` çağrısı tek nokta).

### 4.7 Bildirim ve şikâyet

`GET /bildirimler`, `POST /bildirimler/{id}/okundu`, `POST /bildirimler/hepsi-okundu`
`GET|POST /sikayet/{kind}/{refId}` — kind: `conversation|donation|request|swap_book|user`

### 4.8 Yönetim (`/admin/**`, ROLE_ADMIN)

Pano, öğrenci belgeleri, üyeler, içerik moderasyonu, teslim noktaları, şikâyetler.
Mobil için muhtemelen kapsam dışı; gerekiyorsa `AdminService` ve `ReportService`
üzerinden.

---

## 5. Mobil uygulamanın uyması gereken iş kuralları

Bunlar sunucuda zorlanıyor; mobil taraf **aynı kuralları göstermeli** ki
kullanıcı duvara toslamasın.

### Öğrenci önceliği
Yeni bağış **ilk 48 saat** yalnızca onaylı öğrencilere açık
(`Donation.PRIORITY_WINDOW_HOURS = 48`). Sonra herkese açılır.
`DonationView.isPriorityActive()` ve `getPriorityLeft()` hazır.

### Kota
| Katman | 7 gün | 30 gün |
| --- | --- | --- |
| Öğrenci (`StudentStatus.APPROVED`) | 3 | 10 |
| Üye | 1 | 3 |

Bağıştan alınanlar **ve** karşılanan istekler aynı sayaca girer.
Bağış yapmanın ve takasın sınırı yok. `QuotaService.quotaFor(user)` → `Quota`.

### Alma uygunluğu — `DonationService.eligibility()` kodları
`OWN_DONATION`, `ALREADY_CLAIMED`, `NOT_AVAILABLE`, `LEVEL_MISMATCH`,
`PRIORITY_WINDOW`, `QUOTA`, `ADDRESS_REQUIRED` (yalnızca kargo modu açıkken)
→ Mobil bu kodlara göre düğmeyi kapatıp sebep göstermeli.

### Buluşma
- Geçmişe ayarlanamaz (15 dk tolerans), en fazla **60 gün** sonrası
- **İki taraf da** ayarlayabilir/değiştirebilir
- Teslim onayı için önce buluşma ayarlanmış olmalı
- Pasifleştirilmiş nokta seçilemez

### Gelinmedi
- Yalnızca **buluşma saati geçtikten sonra**
- Yalnızca **karşı taraf** bildirebilir
- Bağışta: kitap havuza döner, **gelmeyenin kota hakkı yanar**
- İstekte: karşılayan gelmediyse istek yeniden açılır
- Takasta: takas iptal, iki kitap yeniden açılır
- `User.noShowCount` artar

### Mesajlaşma
- Sohbet **yalnızca** bir alışveriş üzerinden açılır, serbest DM yok
- Erişim denetimi sohbet kaydından değil **alışverişten** doğrulanır
- Mesaj en fazla 2000 karakter

### Şikâyet
- Kendi içeriğini şikâyet edemezsin
- Aynı şey ikinci kez şikâyet edilemez (açık şikâyet varken)
- Sohbet şikâyetinde taraf olmayan şikâyet edemez
- Yönetici **yalnızca açık şikâyeti olan** sohbeti okuyabilir

### Diğer
- Aynı anda en fazla **5 açık istek** (`RequestService.MAX_OPEN_REQUESTS`)
- Giriş: aynı e-posta + IP için 15 dakikada 8 hatalı denemeden sonra kilit
- Aynı kitap ikinci kez takasa açılamaz

---

## 6. Kimlik doğrulama — mobil için karar gerekiyor

Backend şu an **oturum çerezi** (`JSESSIONID`) + **CSRF token** kullanıyor.
Bu tarayıcı için doğru, mobil için zahmetli.

`FreshPrincipalFilter` her istekte kullanıcıyı veritabanından tazeliyor:
yönetici işlemleri (onay, yetki, askı) anında geçerli oluyor ve askıya alınan
üyenin açık oturumu düşüyor. **Token tabanlı bir API yazılırsa bu davranışın
korunması gerekir** — yoksa askıya alınan kullanıcı token'ı bitene kadar
sistemde kalır.

Seçenekler:
- **Oturum çerezi** — en az iş, mobil HTTP istemcisinde çerez yönetimi gerekir,
  CSRF için `X-CSRF-TOKEN` başlığı gönderilmeli
- **JWT / opak token** — mobil için doğal; iptal (askıya alma) mekanizması
  ayrıca düşünülmeli
- Karma: `/api/**` için token, web için çerez (aynı `SecurityFilterChain`'de
  ayrı matcher'larla)

---

## 7. Domain modeli (JSON şeması çıkarırken)

**Enum'lar** (string olarak taşınmalı):
```
ClaimStatus     MATCHED, ARRANGED, SHIPPED, DELIVERED, NO_SHOW
RequestStatus   OPEN, FULFILLED, ARRANGED, SHIPPED, DELIVERED, NO_SHOW
OfferStatus     PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED
DonationStatus  OPEN, CLOSED
SwapBookStatus  OPEN, CLOSED
StudentStatus   NONE, PENDING, APPROVED, REJECTED
SchoolLevel     ORTAOKUL, LISE, UNIVERSITE
TargetLevel     ORTAOKUL, LISE, UNIVERSITE, HEPSI
DonationSource  PURCHASE, OWN            (purchase kapalı)
ConversationKind CLAIM, REQUEST, SWAP
ReportKind      CONVERSATION, DONATION, REQUEST, SWAP_BOOK, USER
ReportReason    TACIZ, UYGUNSUZ, SPAM, SAHTE, GELMEDI, TICARET, DIGER
ReportStatus    OPEN, ACTIONED, DISMISSED
```

**Varlıklar:** `User`, `Book`, `Donation`, `Claim`, `BookRequest`, `SwapBook`,
`SwapOffer`, `Meeting` (gömülü), `PickupPoint`, `Conversation`, `Message`,
`Notification`, `Report`, `AuthToken`.

`Meeting` gömülü tip olarak `Claim`, `BookRequest` ve `SwapOffer` içinde:
`point`, `note`, `at`, `arrangedAt`, `remindedAt`.

> `User` entity'sini asla doğrudan serileştirme: `passwordHash`, `documentNo`,
> `documentPath`, `address`, `phone` içerir. DTO kullan.

---

## 8. Önerilen çalışma sırası

1. **Kimlik doğrulama kararını ver** (bölüm 6) — gerisi buna bağlı
2. `/api/v1/**` altında `@RestController` katmanı + DTO'lar; iş kuralları
   servislerden gelir, yeniden yazılmaz
3. Sırayla: kimlik → keşif/bağış → buluşma → mesajlaşma → istek/takas → şikâyet
4. Her uç için MockMvc testi (mevcut test altyapısı hazır, örnekler
   `src/test/java/app/kitapla/web/` altında)
5. Mobil ekranları yeni akışa göre yeniden kur (bölüm 2)
6. Özellik bayraklarını API'den yayınla ki mobil, kargo/adres alanlarını
   koşullu gösterebilsin

## 9. Faydalı dosyalar

```
kitapla/src/main/java/app/kitapla/service/    iş kuralları (API bunları çağırmalı)
kitapla/src/main/java/app/kitapla/web/        mevcut HTML controller'lar (referans)
kitapla/src/main/java/app/kitapla/config/SecurityConfig.java
kitapla/src/main/java/app/kitapla/config/Features.java
kitapla/src/test/java/app/kitapla/                 249 test — kuralların canlı belgesi
README.md                                      ürün kuralları
deploy/DOCKER.md                               çalıştırma
```

Yerelde çalıştırma: `docker compose up -d --build` → <http://localhost:8080>
Yönetici: `admin@kitapla.local` / `admin123`
