# Bakım sayfası

`index.html`, KitAppLa uygulaması (`127.0.0.1:8080`) kapalıyken ana makinedeki
Caddy'nin ziyaretçiye gösterdiği sayfadır. `deploy/kitappla.caddy` içindeki
`handle_errors 502 503 504` bloğu bu klasörü (`C:/Project/kitap/kitap/bakim`) sunar.

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

Caddy bloğunda `rewrite * /index.html` vardır: klasördeki her yol bu sayfaya
döner, dolayısıyla klasöre başka bir dosya konsa bile dışarı sızmaz.

## Değişiklik yaptıktan sonra

Sayfa diskten okunduğu için kaydetmek yeterlidir; Caddy'yi yeniden yüklemeye
gerek yok. `deploy/kitappla.caddy`'yi değiştirirsen bkz. `docs/canliya-gecis.md`.

## Sınır

Bu sayfa yalnızca **Caddy ayaktayken** gösterilebilir. Sunucunun tamamı ya
da Caddy servisi kapanırsa alan adına hiç yanıt dönmez.
