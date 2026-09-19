# Canlıya alma

Canlı sunucu bu Windows makinesidir. Ana makinedeki Caddy servisi `www.kitappla.com`,
`admin.kitappla.com`, `kitappla.com` ve eski `kitap.rorcun.com` adreslerini karşılar ve
uygulamaya `127.0.0.1:8080` üzerinden vekillik eder.

| Parça | Dosya |
|---|---|
| Uygulama + PostgreSQL | `docker-compose.yml` (prod profili, yalnızca loopback, kaynak sınırları) |
| Sırlar | `.env` (şablon: `deploy/kitapla.env.ornek`) |
| Caddy site blokları | `deploy/kitappla.caddy` — canlı Caddyfile bunu `import` eder |
| Bakım sayfası | `bakim/index.html` — uygulama kapalıyken Caddy 503 ile sunar |

## İlk geçiş: eski yerel ayardan canlı ayara

Canlı konteyner bir süre yerel geliştirme için yazılmış compose ayarıyla çalıştı (prod profili
kapalı, `0.0.0.0:8080`). `docker-compose.yml` artık canlı ayarın kendisidir; aynı klasörden
`up` çalıştırmak konteynerleri yeni ayarla yeniden oluşturur. Proje adı (`kitap`) ve birimler
(`kitap_kitapla-db-veri`, `kitap_kitapla-dosya`) değişmez, veriler kalır.

| Konu | Önce | Sonra |
|---|---|---|
| Profil | yok (geliştirme varsayılanları) | `prod` |
| Oturum çerezi | `HttpOnly` | `Secure; HttpOnly; SameSite=Lax` |
| Şablon önbelleği | kapalı | açık |
| H2 konsolu (`/h2`) | kayıtlı yol | kapalı |
| Ters vekil başlıkları | işlenmiyor (herkes aynı IP) | işleniyor (giriş sınırı kişi başına) |
| Uygulama portu | `0.0.0.0:8080` | `127.0.0.1:8080` |
| Kaynak sınırı | yok | uygulama 6 CPU / 3 GB, veritabanı 4 CPU / 2 GB |

Tahmini kesinti 1–2 dakikadır; bu sürede Caddy bakım sayfasını gösterir. Açık oturumlar düşer.

## 1. Öncesi

`.env`'de şunlar dolu olmalı: `KITAPLA_DB_PASSWORD`, `KITAPLA_ADMIN_PASSWORD` (prod profili
`admin123` ile açılmaz), `KITAPLA_BASE_URL=https://www.kitappla.com`,
`KITAPLA_ADMIN_URL=https://admin.kitappla.com`.

```bat
cd C:\Project\kitap\kitap

:: Veritabanı ve yüklenen dosyaların yedeği
docker exec kitapla-db pg_dump -U kitapla -d kitapla -Fc -f /tmp/yedek.dump
docker cp kitapla-db:/tmp/yedek.dump kitapla-db-yedek.dump
docker run --rm -v kitap_kitapla-dosya:/v:ro -v "%cd%":/yedek --entrypoint tar postgres:16-alpine czf /yedek/kitapla-dosya-yedek.tgz -C /v .

:: Geri dönüş için çalışan imajı etiketle
docker tag kitap-kitapla:latest kitap-kitapla:onceki
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
docker logs kitapla | findstr /c:"profile is active" /c:"migration"

:: Port yalnızca loopback'te olmalı: 127.0.0.1:8080
docker port kitapla

:: Çerezde Secure ve SameSite=Lax olmalı
curl -sI https://www.kitappla.com/login | findstr /i set-cookie

:: Sağlık ve yönetim alan adı
curl -s -o NUL -w "%{http_code}\n" https://www.kitappla.com/saglik
curl -s -o NUL -w "%{http_code}\n" https://admin.kitappla.com/login
```

Sorgu istatistiği için bir kez (isteğe bağlı):

```bat
docker exec kitapla-db psql -U kitapla -d kitapla -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"
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
docker tag kitap-kitapla:onceki kitap-kitapla:latest
docker compose up -d --no-build
```

Yeni göçler eski imajla uyumludur: Flyway, eski kodun bilmediği ileri göçleri yok sayar.
Veri geri yüklemek gerekirse: `pg_restore --clean -U kitapla -d kitapla` ile `.dump` dosyası.
