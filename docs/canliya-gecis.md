# Canlıya alma

Canlı sunucu bu Windows makinesidir. Ana makinedeki Caddy servisi `www.kitappla.com`,
`admin.kitappla.com`, `kitappla.com` ve eski `kitap.rorcun.com` adreslerini karşılar ve
uygulamaya `127.0.0.1:8080` üzerinden vekillik eder.

| Parça | Dosya |
|---|---|
| Uygulama + PostgreSQL | `docker-compose.yml` (prod profili, yalnızca loopback, kaynak sınırları) |
| Sırlar | `.env` (şablon: `deploy/kitappla.env.ornek`) |
| Caddy site blokları | `deploy/kitappla.caddy` — canlı Caddyfile bunu `import` eder |
| Bakım sayfası | `bakim/index.html` — uygulama kapalıyken Caddy 503 ile sunar |

## Tek seferlik ad geçişi: kitapla → kitappla

Kod ve ayarlar `kitappla` adına geçti (Java paketi `app.kitappla`, ortam değişkenleri `KITAPPLA_*`,
oturum çerezi `KITAPPLA_SESSION`). Canlı sistem bu geçişe kadar eski adlarla çalışır:

| Parça | Eski | Yeni |
|---|---|---|
| Compose projesi | `kitap` (klasör adından) | `kitappla` (`docker-compose.yml` içinde sabit) |
| Konteynerler | `kitapla`, `kitapla-db` | `kitappla`, `kitappla-db` |
| Birimler | `kitap_kitapla-db-veri`, `kitap_kitapla-dosya` | `kitappla_kitappla-db-veri`, `kitappla_kitappla-dosya` |
| İmaj | `kitap-kitapla` | `kitappla-kitappla` |
| PostgreSQL veritabanı / kullanıcı | `kitapla` / `kitapla` | `kitappla` / `kitappla` (parola aynı) |

**Bu geçiş yapılmadan `docker compose up` çalıştırılmamalı:** yeni ayar boş birimlerle yeni bir
veritabanı açar. Geçişi betik yapar; veriyi taşır, satır ve dosya sayılarını karşılaştırır, bir
adım başarısız olursa eski sistemi yeniden başlatır:

```bat
cd C:\Project\kitap\kitap
powershell -ExecutionPolicy Bypass -File deploy\kitappla-ad-gecisi.ps1
```

Kesinti ~1–2 dakikadır (bakım sayfası görünür). Oturum çerezinin adı değiştiği için açık oturumlar
bir kez düşer; "beni hatırla" çerezi olanlar kendiliğinden yeniden girer. Mobil uygulamanın yeni
sürümü (yeni çerez adını bekler) bu geçişten sonra kurulmalıdır.

Eski konteynerler durdurulur ama silinmez, eski birimlere dokunulmaz; betik sonunda geri dönüş ve
temizlik komutlarını yazar. Klasör adı geçişi (`C:\Project\kitap` → `C:\Project\kitappla`) bundan
bağımsızdır: `C:\Project\kitap\klasor-adini-degistir.ps1` (Caddy import yolunu da günceller).

## 1. Öncesi

`.env`'de şunlar dolu olmalı: `KITAPPLA_DB_PASSWORD`, `KITAPPLA_ADMIN_PASSWORD` (prod profili
`admin123` ile açılmaz), `KITAPPLA_BASE_URL=https://www.kitappla.com`,
`KITAPPLA_ADMIN_URL=https://admin.kitappla.com`.

```bat
cd C:\Project\kitappla\kitappla

:: Veritabanı ve yüklenen dosyaların yedeği
docker exec kitappla-db pg_dump -U kitappla -d kitappla -Fc -f /tmp/yedek.dump
docker cp kitappla-db:/tmp/yedek.dump kitappla-db-yedek.dump
docker run --rm -v kitappla_kitappla-dosya:/v:ro -v "%cd%":/yedek --entrypoint tar postgres:16-alpine czf /yedek/kitappla-dosya-yedek.tgz -C /v .

:: Geri dönüş için çalışan imajı etiketle
docker tag kitappla-kitappla:latest kitappla-kitappla:onceki
```

## 2. Geçiş ve sonraki güncellemeler

```bat
docker compose up -d --build
docker compose ps
```

Açılışta Flyway eksik göçleri uygular (ör. `V6__notification_link`, `V7__persistent_logins`).

## 3. Doğrulama

```bat
:: "The following 1 profile is active: \"prod\"" ve Flyway satırları görünmeli
docker logs kitappla | findstr /c:"profile is active" /c:"migration"

:: Port yalnızca loopback'te olmalı: 127.0.0.1:8080
docker port kitappla

:: Çerezde Secure ve SameSite=Lax olmalı
curl -sI https://www.kitappla.com/login | findstr /i set-cookie

:: Sağlık ve yönetim alan adı
curl -s -o NUL -w "%{http_code}\n" https://www.kitappla.com/saglik
curl -s -o NUL -w "%{http_code}\n" https://admin.kitappla.com/login
```

Sorgu istatistiği için bir kez (isteğe bağlı):

```bat
docker exec kitappla-db psql -U kitappla -d kitappla -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"
```

## Caddy

`deploy/kitappla.caddy` değişirse yalnızca Caddy'yi yeniden yükle (kesinti olmaz). Canlı
Caddyfile: `C:\Project\LocalAgentApi-WebU - OpenCode\caddy\Caddyfile`.

```bat
caddy validate --config "C:\Project\LocalAgentApi-WebU - OpenCode\caddy\Caddyfile"
caddy reload   --config "C:\Project\LocalAgentApi-WebU - OpenCode\caddy\Caddyfile"
```

## Geri dönüş

```bat
docker tag kitappla-kitappla:onceki kitappla-kitappla:latest
docker compose up -d --no-build
```

Yeni göçler eski imajla uyumludur: Flyway, eski kodun bilmediği ileri göçleri yok sayar.
Veri geri yüklemek gerekirse: `pg_restore --clean -U kitappla -d kitappla` ile `.dump` dosyası.
