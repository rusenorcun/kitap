# 📚 KİTAPLA

Öğrencilere öncelik veren, herkesin katılabildiği bir **kitap bağış ve takas platformu**.
Spring Boot + Thymeleaf ile sunucu tarafında render edilen, tek parça çalışan bir web uygulamasıdır.

> Yerel kullanım için hazırdır: e-posta gönderimi, ödeme ya da harici servis bağımlılığı yoktur.

## Hızlı başlangıç

### Docker ile (en kolay)

```bash
docker compose up -d --build     # http://localhost:8080
```

Java kurmana gerek yok. Ayrıntılar ve yayına alma için:
**[deploy/DOCKER.md](deploy/DOCKER.md)**.

### Doğrudan (Docker olmadan)

Tek gereksinim **JDK 21**. Maven kurmanıza gerek yok — depodaki sarmalayıcı (`mvnw`) gerekirse
kendi Maven sürümünü indirir.

```bash
cd kitapla
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Uygulama <http://localhost:8080> adresinde açılır. İlk açılışta veritabanı, yönetici hesabı ve
deneme verisi otomatik oluşur.

Tek dosyalık çalıştırılabilir sürüm isterseniz:

```bash
./mvnw -DskipTests package
java -jar target/kitapla-0.1.0.jar
```

| Hesap | E-posta | Şifre | Rolü |
| --- | --- | --- | --- |
| Yönetici | `admin@kitapla.app` | `admin123` | Yönetim paneli |
| Bağışçı | `ayse@ornek.com` | `sifre123` | Üye (belgesiz) |
| Öğrenci | `elif@ornek.com` | `sifre123` | Onaylı öğrenci |
| Başvuru sahibi | `mert@ornek.com` | `sifre123` | Belgesi incelemede |

```bash
./mvnw test                   # 169 test
```

## Hesap modeli

Tek hesap; herkes hem **bağış yapabilir** hem de **kitap alabilir**. Alıcı iki katmandan biridir:

- **Üye** — belgesiz. Bağış yapar, takas eder, kitap alır. Önceliği yoktur, kotası düşüktür.
- **Öğrenci** — belgesi yönetici tarafından onaylanmış üye. Bağışta **48 saat öncelik** ve daha yüksek kota kazanır.

Üye dilediği zaman `/profil/ogrenci` üzerinden öğrenci doğrulamasına başvurur; belge yönetici onayına gider.

## Akışlar

1. **Bağış** — Üye kitap seçip miktar, hedef seviye ve kaynak (satın alıp gönderir / elindeki kopya) belirler. Uygun alıcılar talep eder.
2. **İstek** — Alıcı ihtiyacı olan kitabı listeler; başka biri satın alarak ya da elindeki kopyayla karşılar.
3. **Takas** — Üyeler kitaplarını takasa açar, başkasının kitabına kendi kitabıyla teklif verir. Kabul edilince iki taraf karşılıklı kargolar. Kotadan bağımsızdır.

Her akışta kitap, eşleşen tarafın **teslimat adresine** kargolanır. Adres yalnızca eşleşilen karşı tarafa görünür. Teslimat `eşleşti → kargoda → teslim edildi` (takas: çift kargo → `tamamlandı`) olarak izlenir.

## Kurallar

- **Öğrenci önceliği** — Yeni bağış ilk **48 saat** yalnızca onaylı öğrencilere açıktır; süre dolunca tüm üyelere açılır.
- **Kota** — Öğrenci son 7 günde **3**, 30 günde **10**; üye son 7 günde **1**, 30 günde **3** kitap alabilir. Bağıştan alınanlar ve karşılanan istekler sayıma dahildir. Bağış yapmanın sınırı yoktur.
- **Teslimat adresi** — Kitap almak, istek oluşturmak ve takas için profilde adres tanımlı olmalıdır.
- **Öğrenci belgesi** — Belge numarası + belge dosyası gerekir. Aynı belge numarasıyla iki kayıt olmaz. Belge onaylanana kadar başvuru incelemededir.
- **Kitap kaydı** — Aynı ad + yazar ikinci kez oluşturulmaz (bul ya da oluştur). Alışveriş linki verilirse başlık ve kapak **OpenGraph** ile otomatik doldurulur.
- **Giriş denemesi** — Aynı e-posta + IP için 15 dakikada 8 hatalı denemeden sonra giriş geçici olarak kilitlenir.

## Yönetim paneli (`/admin`)

- **Pano** — üye, öğrenci, bağış, istek ve takas sayaçları; bekleyen belge uyarısı
- **Öğrenci belgeleri** — onayla ya da gerekçeli reddet. Reddedilen belge diskten silinir, üye yeni belgeyle tekrar başvurabilir
- **Üyeler** — arama, askıya alma/aktif etme, yönetici yetkisi verme/alma, kaydı olmayan üyeyi silme
- **İçerik** — açık bağış, istek ve takas ilanlarını gerekçeyle kaldırma

Her yönetim işlemi ilgili üyeye bildirim bırakır. Yönetici işlemleri **anında** geçerli olur; ilgili üyenin yeniden giriş yapması gerekmez, askıya alınan üyenin açık oturumu da hemen düşer.

Öğrenci belgeleri hiçbir zaman herkese açık servis edilmez; yalnızca `/admin/belge/{id}` ucundan, yönetici oturumuyla görüntülenir.

## Yayına alma

İki yol var:

- **Docker ile** — `docker-compose.prod.yml`: uygulama ve Caddy birlikte ayağa
  kalkar, tek komut. Bkz. **[deploy/DOCKER.md](deploy/DOCKER.md)**.
- **Doğrudan sunucuya** — systemd + sistem Caddy'si. Bkz.
  **[deploy/README.md](deploy/README.md)**.

Her ikisi de `deploy/Caddyfile` dosyasını paylaşır; alan adı ve hedef ortam
değişkeninden okunur.

```bash
./mvnw -DskipTests package
java -jar target/kitapla-0.1.0.jar --spring.profiles.active=prod
```

`prod` profili uygulamayı yalnızca `127.0.0.1`'e bağlar, oturum çerezini
`Secure` yapar, şablon önbelleğini açar, H2 konsolunu kapatır ve ters vekilin
ilettiği `X-Forwarded-*` başlıklarını dikkate alır.

## Yapılandırma

`kitapla/src/main/resources/application.properties` içinden ya da ortam değişkenleriyle:

| Değişken | Açıklama | Varsayılan |
| --- | --- | --- |
| `KITAPLA_ADMIN_EMAIL` | Açılışta oluşturulacak yönetici | `admin@kitapla.app` |
| `KITAPLA_ADMIN_PASSWORD` | Yönetici şifresi | `admin123` |
| `KITAPLA_ADMIN_NAME` | Yönetici adı | `Yönetici` |
| `KITAPLA_CONTACT_EMAIL` | İletişim sayfasında gösterilen adres | yönetici e-postası |
| `SERVER_PORT` | Sunucu portu | `8080` |

Varsayılan yönetici şifresi kullanıldığında açılışta uyarı loglanır. Yerel deneme dışında mutlaka değiştirin:

```bash
KITAPLA_ADMIN_PASSWORD=guclu-parola ./mvnw spring-boot:run
```

> Ortam değişkeniyle **Türkçe karakter** içeren bir değer verecekseniz (ör. yönetici adı)
> kabuğun UTF-8 yerelinde olması gerekir; aksi halde JVM değeri ASCII olarak okur:
> `LANG=C.UTF-8 KITAPLA_ADMIN_NAME="Baş Yönetici" ./mvnw spring-boot:run`

Diğer ayarlar: `kitapla.upload-dir` (yüklenen dosyalar, varsayılan `./uploads`),
`kitapla.login.max-attempts`, `kitapla.login.window-minutes`.

## Teknoloji

| Katman | Seçim |
| --- | --- |
| Çalışma zamanı | Java 21, Spring Boot 3.3 |
| Web | Spring MVC + Thymeleaf (sunucu tarafı render) |
| Dinamik parçalar | HTMX (filtreler, link önizleme, bildirim okundu işaretleme) |
| Veri | Spring Data JPA + H2 (dosya tabanlı; testlerde bellek içi) |
| Güvenlik | Spring Security (form girişi, BCrypt, CSRF) |
| Bağımlılık | Jsoup (OpenGraph başlık/kapak çıkarımı) |

`spring.jpa.open-in-view` kapalıdır; şablonların eriştiği ilişkiler repository sorgularında `join fetch` ile çekilir.

## Proje yapısı

```
kitapla/
  src/main/java/app/kitapla/
    domain/      JPA varlıkları ve enum'lar
    repo/        Spring Data repository'leri
    service/     iş kuralları (kota, öncelik, moderasyon)
    security/    kimlik doğrulama, oturum tazeleme, giriş sınırı
    web/         controller'lar
    config/      güvenlik yapılandırması ve deneme verisi
  src/main/resources/
    templates/   Thymeleaf şablonları
    static/css/  tasarım sistemi
```

## Sayfalar

Herkese açık: ana sayfa, keşfet, kitap detayı, açık istekler, SSS, **topluluk kuralları**,
**gizlilik** ve **iletişim**. Giriş sonrası: panom, bağış/istek/takas akışları, teslimat takibi,
profil, öğrenci başvurusu ve bildirimler. Yöneticiye ayrıca `/admin` altındaki dört sayfa açılır.

## Tasarım

Renk paleti: Saman Kağıdı `#F3EAD3`, Koyu Espresso `#3E2723`, Tarçın `#C65D47`, Soluk Adaçayı `#8FA89B`.
Karanlık tema `prefers-color-scheme` ile otomatik gelir. Logo üç kitap sırtından oluşur.

## Testler

```bash
cd kitapla && ./mvnw test
```

169 test; iş kuralları (kota, öncelik penceresi, takas durumları, moderasyon) servis testleriyle,
sayfa akışları ve erişim denetimi MockMvc testleriyle doğrulanır.
