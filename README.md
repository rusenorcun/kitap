# KitAppLa

Öğrencilere öncelik veren, herkesin katılabildiği bir **kitap bağış ve takas platformu**.
Spring Boot + Thymeleaf ile sunucu tarafında render edilen web sitesi; mobil uygulama için
aynı iş kurallarını kullanan bir JSON API (`/api/v1`) de sunar.

- Site: <https://www.kitappla.com>
- Yönetim: <https://admin.kitappla.com>

## Depo düzeni

```
kitappla/                 uygulama (Maven projesi, Dockerfile)
  src/main/java/app/kitappla/
    domain/               JPA varlıkları ve enum'lar
    repo/                 Spring Data repository'leri
    service/              iş kuralları (kota, öncelik, moderasyon, posta, SSE)
    security/             kimlik doğrulama, beni hatırla, giriş sınırı
    web/                  sayfa controller'ları
    api/                  mobil JSON API (v1) ve DTO'lar
    config/               güvenlik, marka, önbellek, açılış verisi
  src/main/resources/
    templates/            Thymeleaf şablonları (mail/ ve error/ dahil)
    static/               tasarım sistemi (css) ve favicon
    db/migration/         Flyway göçleri (PostgreSQL)
docker-compose.yml        canlı sunucu: uygulama + PostgreSQL
deploy/kitappla.caddy     canlı Caddy site blokları
deploy/kitappla.env.ornek  .env şablonu
bakim/                    uygulama kapalıyken Caddy'nin sunduğu bakım sayfası
docs/canliya-gecis.md     canlıya alma, doğrulama ve geri dönüş
```

## Yerel geliştirme

Tek gereksinim **JDK 21**; Maven sarmalayıcısı (`mvnw`) gerekirse kendi Maven sürümünü indirir.
Yerelde dosya tabanlı H2 veritabanı kullanılır (`kitappla/data/`), örnek veri açıktır ve posta
gönderilmez (iletiler günlüğe yazılır).

```bash
cd kitappla
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run  →  http://localhost:8080
./mvnw test
```

| Hesap | E-posta | Şifre | Rolü |
| --- | --- | --- | --- |
| Yönetici | `admin@kitappla.app` | `admin123` | Yönetim paneli |
| Bağışçı | `ayse@ornek.com` | `sifre123` | Üye |
| Öğrenci | `elif@ornek.com` | `sifre123` | Onaylı öğrenci |
| Başvuru sahibi | `mert@ornek.com` | `sifre123` | Belgesi incelemede |

Bu hesaplar yalnızca yerelde oluşur: canlıda örnek veri kapalıdır ve `prod` profili
`admin123` şifresiyle açılmayı reddeder.

## Canlıya alma

Canlı sunucu bu makinedir: ana makinedeki Caddy servisi HTTPS'i ve alan adlarını karşılar,
uygulama ile PostgreSQL `docker-compose.yml` ile konteynerde çalışır ve yalnızca
`127.0.0.1:8080`'i dinler.

```bat
cd C:\Project\kitappla\kitappla
docker compose up -d --build
```

Yedek, doğrulama ve geri dönüş adımları: **[docs/canliya-gecis.md](docs/canliya-gecis.md)**.

`prod` profili oturum çerezini `Secure` yapar, şablon önbelleğini açar, H2 konsolunu kapatır,
ters vekilin ilettiği `X-Forwarded-*` başlıklarını dikkate alır ve şemayı Flyway ile yönetir
(`ddl-auto=validate`). Yeni bir şema değişikliği `kitappla/src/main/resources/db/migration/`
altına yeni bir `V<n>__aciklama.sql` dosyası olarak eklenir.

## Yapılandırma

Canlı ayarlar depo kökündeki `.env` dosyasından okunur (depoya girmez); şablon ve açıklamalar
`deploy/kitappla.env.ornek` içindedir. Başlıcaları:

| Değişken | Açıklama |
| --- | --- |
| `KITAPPLA_NAME`, `KITAPPLA_DOMAIN` | Görünen marka adı ve alan adı |
| `KITAPPLA_BASE_URL` | Postadaki bağlantıların kök adresi |
| `KITAPPLA_ADMIN_URL` | Yönetimin ayrı alan adı (boşsa `/admin` sitenin içinde) |
| `KITAPPLA_ADMIN_EMAIL` / `_PASSWORD` / `_NAME` | Açılışta oluşturulan/güncellenen yönetici |
| `KITAPPLA_DB_*` | PostgreSQL bağlantısı |
| `KITAPPLA_MAIL_ENABLED`, `KITAPPLA_SMTP_*` | E-posta gönderimi |

Kapalı akışlar bayrakla geri açılabilir (kod ve sütunlar yerinde durur): `KITAPPLA_DOCUMENT`
(belgeyle öğrenci başvurusu), `KITAPPLA_SHIPPING` (kargo), `KITAPPLA_PURCHASE` (satın alıp gönder),
`KITAPPLA_ADDRESS` (teslimat adresi).

> `application.properties` ISO-8859-1 okunur: Türkçe karakterli değerler `\u` kaçışıyla yazılır.

## Hesap modeli

Tek hesap; herkes hem **bağış yapabilir** hem de **kitap alabilir**. Alıcı iki katmandan biridir:

- **Üye** — okul adresi doğrulanmamış. Bağış yapar, takas eder, kitap alır; kotası düşüktür.
- **Öğrenci** — okul e-postası (`.edu.tr`) doğrulanmış üye. Bağışta **48 saat öncelik** ve daha
  yüksek kota kazanır. Okul adresi kayıtta ya da sonradan `/profil/ogrenci` üzerinden eklenir;
  adrese gönderilen bağlantıyla doğrulanır. Bir okul adresi yalnızca bir hesaba bağlanabilir.

## Akışlar

1. **Bağış** — Üye kitap seçer, miktar ve hedef seviyeyi belirler, teslim edeceği kampüs noktasını önerir. Uygun alıcılar talep eder.
2. **İstek** — Alıcı ihtiyacı olan kitabı listeler; başka biri elindeki kopyayla karşılar.
3. **Takas** — Üyeler kitaplarını takasa açar, başkasının kitabına kendi kitabıyla teklif verir. Kabul edilince kampüste buluşup karşılıklı verirler. Kotadan bağımsızdır.

Teslim **kampüs içinde yüz yüze** yapılır; ev adresi paylaşılmaz. Eşleşen taraflar mesajlaşarak
yer ve saatte anlaşır, buluşmadan önce hatırlatma düşer. Akış
`eşleşti → buluşma ayarlandı → teslim edildi`; karşı taraf gelmezse **gelinmedi** olarak
işaretlenir (kitap havuza döner, gelmeyenin kota hakkı yanar). Kural dışı içerik **şikâyet**
edilebilir.

## Kurallar

- **Öğrenci önceliği** — Yeni bağış ilk **48 saat** yalnızca doğrulanmış öğrencilere açıktır.
- **Kota** — Öğrenci son 7 günde **3**, 30 günde **10**; üye son 7 günde **1**, 30 günde **3** kitap alabilir. Bağış yapmanın sınırı yoktur.
- **Kitap kaydı** — Aynı ad + yazar ikinci kez oluşturulmaz. Alışveriş linki verilirse başlık ve kapak OpenGraph ile doldurulur.
- **Giriş denemesi** — Aynı e-posta + IP için 15 dakikada 8 hatalı denemeden sonra giriş geçici olarak kilitlenir.
- **Askıya alma** — Askıya alınan üyenin süren talep, istek ve takasları iptal edilir, karşı tarafın hakkı iade edilir.

## Yönetim paneli

Pano sayaçları; üye arama, askıya alma, yönetici yetkisi; teslim noktaları; ilan kaldırma;
şikâyetler. Yönetim işlemleri anında geçerli olur ve ilgili üyeye bildirim bırakır.
`KITAPPLA_ADMIN_URL` tanımlıysa yönetim sayfaları yalnızca o alan adında açılır.

## Teknoloji

| Katman | Seçim |
| --- | --- |
| Çalışma zamanı | Java 21, Spring Boot 3.3 |
| Web | Spring MVC + Thymeleaf, HTMX, SSE (canlı bildirim ve mesaj) |
| Veri | Spring Data JPA; canlıda PostgreSQL 16 + Flyway, yerelde ve testlerde H2 |
| Güvenlik | Spring Security (form girişi, BCrypt, CSRF, kalıcı "beni hatırla") |
| Önbellek | Caffeine |
| Diğer | Jsoup (OpenGraph), Spring Mail |

`spring.jpa.open-in-view` kapalıdır; şablonların eriştiği ilişkiler repository sorgularında
`join fetch` ile çekilir.
