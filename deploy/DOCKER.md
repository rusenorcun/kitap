# Docker ile çalıştırma

Docker'ı daha önce hiç kullanmadıysan buradan başla. Kısaca: Docker, uygulamayı
çalışması için gereken her şeyle (Java dahil) tek bir paket hâline getirir.
Sunucuya Java kurmana gerek kalmaz, "bende çalışıyordu" sorunu ortadan kalkar.

## Bilmen gereken üç kavram

| Kavram | Ne demek |
| --- | --- |
| **imaj** (image) | Uygulamanın kalıbı. `docker compose build` ile üretilir. |
| **konteyner** (container) | Kalıptan çalışan örnek. Silinebilir; içindeki dosyalar da gider. |
| **birim** (volume) | Konteynerin dışında duran kalıcı depo. Veriler burada saklanır. |

> Önemli: konteyneri silmek veriyi silmez, çünkü veritabanı ve yüklenen dosyalar
> birimlerde (`kitapla-veri`, `kitapla-dosya`) tutulur.

## Yerel deneme

Gereken tek şey Docker Desktop (Windows/Mac) ya da Docker Engine (Linux).

### Windows'ta adım adım

1. **Docker Desktop'ı aç** ve sol altta "Engine running" yazısını gör.
   Docker Desktop kapalıyken komutlar "cannot connect to the Docker daemon" hatası verir.

2. **Proje klasörünü aç.** Dosya Gezgini'nde projenin klasörüne gir
   (`docker-compose.yml` dosyasının göründüğü klasör), adres çubuğuna
   `powershell` yazıp Enter'a bas. O klasörde PowerShell açılır.

3. **Başlat:**

   ```powershell
   docker compose up -d --build
   ```

   İlk çalıştırmada Java indirilir ve proje derlenir; **3-6 dakika sürebilir**
   ve internet bağlantısı gerekir. Sonraki başlatmalar saniyeler alır.

4. **Hazır olmasını bekle:**

   ```powershell
   docker compose ps
   ```

   `STATUS` sütununda `healthy` yazana kadar bekle (ilk açılışta ~1 dakika).
   `starting` görüyorsan henüz açılıyor demektir.

5. **Tarayıcıda aç:** <http://localhost:8080>

Durdurmak için `docker compose down`. Verilerin silinmez.

> PowerShell'de komutlar aynıdır. Bu belgedeki `$PWD` geçen tek komut
> (yedekleme) PowerShell'de `${PWD}` olarak yazılmalıdır.

### Kısaca (Linux/Mac)

```bash
docker compose up -d --build
```

İlk çalıştırma birkaç dakika sürer (Java indirilir, proje derlenir).
Sonrası saniyeler alır. Ardından: <http://localhost:8080>

| Hesap | E-posta | Şifre |
| --- | --- | --- |
| Yönetici | `admin@kitapla.local` | `admin123` |
| Bağışçı | `ayse@ornek.com` | `sifre123` |
| Öğrenci | `elif@ornek.com` | `sifre123` |

Günlük komutlar:

```bash
docker compose logs -f          # günlükleri izle (Ctrl+C ile çık)
docker compose ps               # durum ve sağlık kontrolü
docker compose restart          # yeniden başlat
docker compose down             # durdur (VERİLER KALIR)
docker compose up -d --build    # kod değiştiyse yeniden derle ve başlat
```

### Şifre sıfırlama postasını yerelde görmek

Yerel kurulumda posta gönderimi kapalıdır; gönderilecek ileti günlüğe yazılır:

```bash
docker compose logs -f kitapla | grep "POSTA KAPALI"
```

Gerçek posta denemek istersen `docker-compose.yml` içindeki
`KITAPLA_MAIL_ENABLED` değerini `"true"` yapıp SMTP bilgilerini ekle.

### Her şeyi sıfırlamak

```bash
docker compose down -v          # -v: BİRİMLERİ DE SİLER, tüm veri gider
```

### Veritabanına DBeaver ile bağlanmak

Ayrı bir veritabanı konteyneri yok: uygulama **H2**'yi dosya modunda kullanır
(`kitapla-veri` biriminde `kitapla.mv.db`). `docker-compose.yml`, H2'nin TCP
sunucusunu sabit **9092** portunda açar ve bunu yalnızca `127.0.0.1`'e yayınlar.

H2 bu sunucuyu bir anahtarla korur; anahtar **her konteyner başlatılışında değişir**
ve kilit dosyasından okunur:

```bash
docker exec kitapla sed -n "s/^id=//p" /uygulama/data/kitapla.lock.db
```

DBeaver'da **Yeni Bağlantı → H2 Server** seç (sürücü sürümü 2.x olmalı; DBeaver
sürücüyü ilk seferde kendisi indirir) ve şunları gir:

| Alan | Değer |
| --- | --- |
| URL | `jdbc:h2:tcp://localhost:9092/<yukarıdaki anahtar>` |
| Kullanıcı | `sa` |
| Şifre | *(boş)* |

Uygulama çalışırken bağlanabilirsin; tablolar `PUBLIC` şemasındadır. Konteyneri
yeniden başlattıktan sonra bağlantı kesilirse anahtarı yeniden okuyup URL'yi güncelle.
Tarayıcıdan hızlı bakış için <http://localhost:8080/h2> adresindeki H2 konsolu da
açıktır (JDBC URL: `jdbc:h2:file:./data/kitapla`, kullanıcı `sa`, şifre boş).

## Aynı sunucuda birden fazla site (ortak Caddy)

Aynı makinede başka projelerin de varsa Caddy'yi ortaklaştırıp her şeyi tek
yerden yönetebilirsin: `docker-compose.sunucu.yml` + `deploy/Caddyfile.sunucu`.

- Caddy konteynerde çalışır ve 80/443'ü o dinler
- KİTAPLA aynı Docker ağında, portu ana makineye hiç yayınlanmaz
- **Ana makinede çalışan diğer projeler oldukları yerde kalır**; Caddy onlara
  `host.docker.internal` üzerinden ulaşır. O projelere dokunmak gerekmez.

Önce ana makinedeki Caddy durdurulmalıdır (ikisi de 80/443 ister).

```bash
cp .env.ornek .env  &&  nano .env
docker compose -f docker-compose.sunucu.yml up -d --build
```

> Konteynerin içinden `127.0.0.1` konteynerin **kendisi** demektir, ana makine
> değil. Ana makinedeki servislere `host.docker.internal` ile erişilir.

### Caddy'de sık yapılan bir hata

Yönergeler yazdığın sırayla değil, Caddy'nin öntanımlı sırasına göre işlenir.
`handle` bloğu `respond`'dan önce gelir; bu yüzden

```
handle { reverse_proxy ... }      # her şeyi yakalar
@scanners path /wp-admin* ...
respond @scanners 404             # buraya HİÇ ulaşılmaz
```

yazarsan tarama engeli sessizce çalışmaz. Doğrusu, kuralı catch-all'dan önce
bir `handle` bloğuna koymaktır:

```
@scanners path /wp-admin* ...
handle @scanners { respond 404 }  # önce bu
handle { reverse_proxy ... }      # sonra catch-all
```

## Sunucunda ZATEN Caddy varsa (ona dokunmadan)

Sunucuda başka bir site için çalışan bir Caddy kurulumun varsa
`docker-compose.prod.yml` **kullanılmaz** — o dosya kendi Caddy konteynerini
başlatıp 80/443 portlarını almaya çalışır ve mevcut sitenle çakışır.

Bunun yerine:

```bash
cp .env.ornek .env
nano .env                                    # alan adı, parola, SMTP
docker compose -f docker-compose.yayin.yml up -d --build
```

Bu kurulumda konteyner yalnızca `127.0.0.1:8080` dinler. Ardından
`deploy/kitap-site.caddy` içeriğini mevcut Caddyfile'ının sonuna ekle:

```bash
sudo nano /etc/caddy/Caddyfile               # bloğu en alta yapıştır
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy                  # reload: mevcut sitede kesinti olmaz
```

Caddy her alan adını ayrı yönetir; mevcut siten etkilenmez. Bu kurulum
(mevcut site + eklenen blok) uçtan uca denendi: eski site çalışmaya devam
etti, yeni alan adı HTTPS ile açıldı, uygulama dışarıdan doğrudan
erişilemez kaldı.

## Yayına alma (sunucuda Caddy YOKSA)

Sunucuda, alan adının DNS A kaydı sunucunun IP'sine baktıktan sonra:

```bash
cp .env.ornek .env
nano .env                       # alan adı, yönetici parolası, SMTP bilgileri
docker compose -f docker-compose.prod.yml up -d --build
```

Bu kurulumda:

- **Caddy** 80 ve 443'ü dinler, sertifikayı Let's Encrypt'ten kendi alır ve yeniler
- **Uygulamanın portu ana makineye hiç yayınlanmaz**; ona yalnızca Caddy erişir
- Uygulama `prod` profiliyle çalışır (güvenli çerez, H2 konsolu kapalı,
  ters vekil başlıkları açık)
- Caddy, uygulama **sağlıklı** olduğunu bildirene kadar bekler

Kontrol:

```bash
docker compose -f docker-compose.prod.yml ps      # ikisi de "healthy" olmalı
docker compose -f docker-compose.prod.yml logs -f
curl -I https://alanadin.com
```

## Yedekleme

Tüm durum iki birimde: veritabanı ve yüklenen dosyalar.

```bash
docker run --rm \
  -v kitap_kitapla-veri:/veri -v kitap_kitapla-dosya:/dosya \
  -v "$PWD":/yedek alpine \
  tar czf /yedek/kitapla-$(date +%F).tar.gz /veri /dosya
```

> Birim adlarının başındaki `kitap_` öneki, projenin bulunduğu klasörün adından
> gelir. `docker volume ls` ile gerçek adları görebilirsin.

## Sık karşılaşılanlar

**"port is already allocated"** — 8080 portu başka bir şey tarafından kullanılıyor.
`docker-compose.yml` içinde `"8080:8080"` yerine `"9090:8080"` yazıp
<http://localhost:9090> adresini kullan.

**Değişiklik yaptım ama yansımadı** — `--build` eklemeyi unutmuş olabilirsin:
`docker compose up -d --build`

**Konteyner sürekli yeniden başlıyor** — `docker compose logs kitapla` ile sebebe bak.
Genelde `.env` içinde eksik bir değişkendir.

**Sağlık kontrolü "starting" takılı kaldı** — ilk açılış 60 saniyeye kadar sürebilir;
`docker compose ps` çıktısında `healthy` olmasını bekle.

**Windows: "cannot connect to the Docker daemon"** — Docker Desktop çalışmıyor.
Aç ve "Engine running" yazısını bekle.

**Windows: "docker: command not found"** — Docker Desktop kurulumundan sonra
PowerShell penceresini kapatıp yeniden aç; PATH yenilenmiş olur.

**Windows: derleme çok uzun sürüyor / takılıyor** — Docker Desktop → Settings →
Resources bölümünden en az 4 GB bellek ayrıldığından emin ol.
