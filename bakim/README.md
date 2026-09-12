# Bakım sayfası

`index.html`, KİTAPLA uygulaması (`127.0.0.1:8080` ya da `kitapla:8080`)
kapalıyken Caddy'nin ziyaretçiye gösterdiği sayfadır. **Tüm dağıtım yolları
bu tek dosyayı sunar**; daha önce üç ayrı kopya vardı ve biri diğerlerinden
farklı görünüyordu.

## Sayfanın kendisi

Uygulama çöktüğünde sunulacağı için tamamen kendi kendine yeter: dış CSS,
dış betik ve görsel dosyası yoktur. Renkler `kitapla.css`'teki tasarım
sistemiyle aynıdır (açık ve koyu tema), logo `fragments.html`'deki
logoyla birebir aynı SVG'dir. Yazı tipi uzaktan denenir; gelmezse sistem
yazı tipine düşer ve sayfa yine doğru görünür.

`503 Service Unavailable` ile döner ve `noindex` taşır, böylece arama
motorları bakım metnini sitenin içeriği sanmaz.

Sayfadaki küçük betik 15 saniyede bir sunucuyu yoklar; yanıt 5xx olmaktan
çıktığı anda sayfayı kendiliğinden yeniler.

## Hangi yapılandırma nereye bakıyor

| Dağıtım | Dosya | Bakım sayfasının kökü |
|---|---|---|
| Ana makinedeki `caddy.exe` (kitap.rorcun.com) | `deploy/Caddyfile.rorcun-ornek` | `C:/Project/kitap/kitap/bakim` |
| Docker + ortak Caddy | `deploy/Caddyfile.sunucu` | `/bakim` (compose'da bağlanır) |
| Docker, tek alan adı | `deploy/Caddyfile` | `/bakim` (compose'da bağlanır) |
| Mevcut Caddy'ye eklenen blok | `deploy/kitap-site.caddy` | sunucudaki depo yolu |

Docker tarafında klasör compose dosyalarında salt okunur bağlanır:

```yaml
- ${BAKIM_KLASORU:-./bakim}:/bakim:ro
```

Başka bir klasörden sunmak istersen `.env` içine `BAKIM_KLASORU` yaz.
Doğrudan kurulumda (systemd) `root *` satırını deponun sunucudaki yoluna
göre düzelt.

Her blokta `rewrite * /index.html` vardır: klasördeki her yol bu sayfaya
döner, dolayısıyla klasöre başka bir dosya konsa bile dışarı sızmaz.

## Değişiklik yaptıktan sonra

Sayfa diskten okunduğu için Caddy'yi yeniden başlatmaya gerek yok;
kaydetmek yeterlidir. Yalnızca Caddyfile'ı değiştirirsen:

```
caddy validate --config Caddyfile
caddy reload  --config Caddyfile
```

Docker'da:

```
docker compose -f docker-compose.sunucu.yml restart caddy
```

## Sınır

Bu sayfa yalnızca **Caddy ayaktayken** gösterilebilir. Sunucunun tamamı ya
da Caddy servisi kapanırsa alan adına hiç yanıt dönmez.
