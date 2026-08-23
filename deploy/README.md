# Yayına alma

KİTAPLA'yı bir sunucuda, Caddy arkasında HTTPS ile çalıştırma adımları.
Bu dizindeki dosyalar bir kapsayıcıda gerçek Caddy ile denendi; aşağıdaki
davranışlar doğrulanmıştır (bkz. *Doğrulananlar*).

## Gereksinimler

- Ubuntu/Debian bir sunucu (1 GB RAM yeterli)
- **JDK 21** (`sudo apt install openjdk-21-jre-headless`)
- **Caddy 2** ([resmi kurulum](https://caddyserver.com/docs/install#debian-ubuntu-raspbian))
- Sunucunun IP'sine bakan bir alan adı (A / AAAA kaydı)
- 80 ve 443 portları dışarı açık (Caddy sertifika için 80'i kullanır)

## 1. Uygulamayı derle

Geliştirme makinende:

```bash
cd kitapla
./mvnw -DskipTests package
# target/kitapla-0.1.0.jar
```

## 2. Sunucuya yerleştir

```bash
sudo useradd --system --home /opt/kitapla --shell /usr/sbin/nologin kitapla
sudo mkdir -p /opt/kitapla/data /opt/kitapla/uploads
sudo chown -R kitapla:kitapla /opt/kitapla

scp target/kitapla-0.1.0.jar sunucu:/tmp/
sudo mv /tmp/kitapla-0.1.0.jar /opt/kitapla/kitapla.jar
sudo chown kitapla:kitapla /opt/kitapla/kitapla.jar
```

## 3. Sırları ayarla

```bash
sudo cp deploy/kitapla.env.ornek /etc/kitapla.env
sudo chmod 600 /etc/kitapla.env
sudo nano /etc/kitapla.env      # parolayı ve adresleri kendine göre doldur
```

Yönetici parolasını **mutlaka** değiştir. Uzun ve rastgele bir tane üret:

```bash
openssl rand -base64 24
```

## 4. Servisi kur

```bash
sudo cp deploy/kitapla.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now kitapla
sudo systemctl status kitapla
journalctl -u kitapla -f          # açılış günlüğü
```

Uygulama yalnızca `127.0.0.1:8080` dinler; dışarıdan doğrudan erişilemez.

## 5. Caddy'yi yapılandır

`deploy/Caddyfile` içindeki `kitapla.example.com` yerine kendi alan adını yaz:

```bash
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
sudo nano /etc/caddy/Caddyfile
sudo mkdir -p /var/log/caddy && sudo chown caddy:caddy /var/log/caddy
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Caddy sertifikayı Let's Encrypt'ten kendisi alır ve yeniler; HTTP → HTTPS
yönlendirmesini de otomatik yapar. Ayrıca bir şey tanımlaman gerekmez.

## 6. Kontrol et

```bash
curl -I https://alanadin.com                 # 200 ve güvenlik başlıkları
curl -I http://alanadin.com                  # 308 → https
```

Tarayıcıda gir, yönetici hesabıyla oturum aç, `/admin` panosunu gör.

## Güncelleme

```bash
./mvnw -DskipTests package
scp target/kitapla-0.1.0.jar sunucu:/tmp/
sudo systemctl stop kitapla
sudo mv /tmp/kitapla-0.1.0.jar /opt/kitapla/kitapla.jar
sudo chown kitapla:kitapla /opt/kitapla/kitapla.jar
sudo systemctl start kitapla
```

Veritabanı şeması `ddl-auto=update` ile kendini günceller.

## Yedekleme

Tüm durum iki yerde: `/opt/kitapla/data` (veritabanı) ve `/opt/kitapla/uploads`
(kapaklar ve öğrenci belgeleri).

```bash
sudo systemctl stop kitapla
sudo tar czf /root/kitapla-$(date +%F).tar.gz -C /opt/kitapla data uploads
sudo systemctl start kitapla
```

> H2 dosya veritabanı tek süreç içindir. Servis dururken yedek almak en
> güvenlisidir. Trafik arttığında PostgreSQL'e geçmek gerekir.

## Doğrulananlar

Bu yapılandırma gerçek Caddy 2.8 ile, `tls internal` sertifikası üzerinden
uçtan uca denendi:

| Kontrol | Sonuç |
| --- | --- |
| Uygulama yalnızca 127.0.0.1 dinliyor | dış IP'den bağlantı reddedildi |
| HTTPS üzerinden sayfalar | 200 |
| Giriş sonrası yönlendirme şeması | `https://` (prod profili olmadan `http://`'ye düşüyor) |
| Oturum çerezi | `Secure` + `HttpOnly` |
| HSTS, nosniff, X-Frame-Options, Referrer-Policy, Permissions-Policy | mevcut |
| `Server` başlığı | gizlendi |
| Statik varlık önbelleği | `public, max-age=604800` (tek başlık) |
| HTML sayfaları | `no-store` korunuyor |
| H2 konsolu (yönetici oturumuyla bile) | 404 |
| Öğrenci belgeleri doğrudan URL ile | erişilemiyor |

## Bilinen sınırlar

- **E-posta yok.** Şifre sıfırlama e-postası gönderilmez; şifresini unutan
  kullanıcının parolasını yönetici yeniler. `/iletisim` sayfası bunu açıkça yazar.
- **H2 dosya veritabanı.** Küçük ölçek için yeterli; yoğunlaşırsa PostgreSQL'e taşınmalı.
- **Tek sunucu.** Oturumlar bellekte tutulur; birden fazla kopya çalıştırılamaz.
