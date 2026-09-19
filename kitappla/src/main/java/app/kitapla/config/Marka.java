package app.kitapla.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tek yapılandırmadan marka: ad, alan adı, site adresi ve e-posta kimliği.
 * <p>
 * Tüm görünür ad ve adresler ortam değişkenlerinden gelir; kod ve şablonlar
 * sabit marka metni taşımaz. Alan adı değişince bağlantılar, gönderici ve
 * iletişim adresi kendiliğinden yeni alan adına uyum sağlar.
 */
@Component
public class Marka {

    private final String ad;
    private final String domain;
    private final String siteAdresi;
    private final String gonderenAdres;
    private final String gonderenAd;
    private final String iletisimEpostasi;
    /** Yönetim ayrı alan adındaysa kökü (ör. https://admin.kitappla.com); tek alan adında null. */
    private final String yonetimAdresi;

    public Marka(
            @Value("${kitapla.name:}") String ad,
            @Value("${kitapla.domain:kitappla.com}") String domain,
            @Value("${kitapla.base-url:}") String siteAdresi,
            @Value("${kitapla.mail.from:}") String gonderenAdres,
            @Value("${kitapla.mail.from-name:}") String gonderenAd,
            @Value("${kitapla.contact.email:}") String iletisimEpostasi,
            @Value("${kitapla.admin-url:}") String yonetimAdresi) {
        this.ad = ad.isBlank() ? "KitAppLa" : ad.trim();
        this.yonetimAdresi = yonetimAdresi == null || yonetimAdresi.isBlank()
                ? null : yonetimAdresi.trim().replaceAll("/+$", "");
        this.domain = normalizeDomain(domain);
        String taban = siteAdresi.isBlank() ? null : siteAdresi.trim().replaceAll("/+$", "");
        this.siteAdresi = taban != null ? taban : "https://" + this.domain;
        this.gonderenAdres = gonderenAdres.isBlank() ? "info@" + this.domain : gonderenAdres.trim();
        this.gonderenAd = gonderenAd.isBlank() ? this.ad : gonderenAd.trim();
        this.iletisimEpostasi = iletisimEpostasi.isBlank() ? this.gonderenAdres : iletisimEpostasi.trim();
    }

    private static String normalizeDomain(String d) {
        String s = d == null ? "" : d.trim();
        int bolum = s.indexOf("://");
        if (bolum >= 0) s = s.substring(bolum + 3);
        if (s.startsWith("//")) s = s.substring(2);
        int yol = s.indexOf('/');
        if (yol >= 0) s = s.substring(0, yol);
        int kapı = s.indexOf(':');
        if (kapı >= 0) s = s.substring(0, kapı);
        return s.toLowerCase(java.util.Locale.ROOT);
    }

    /** Görünen ürün adı (ör. sayfa başlıkları, e-posta başlıkları). */
    public String ad() {
        return ad;
    }

    /** Yalnızca ana makine adı (ör. kitap.example.org). */
    public String domain() {
        return domain;
    }

    /** Kök site adresi, sondaki eğik çizgisiz (ör. https://kitap.example.org). */
    public String siteAdresi() {
        return siteAdresi;
    }

    /** E-posta gönderici adresi. */
    public String gonderenAdres() {
        return gonderenAdres;
    }

    /** E-posta görünen gönderici adı. */
    public String gonderenAd() {
        return gonderenAd;
    }

    /** İletişim sayfasında gösterilen e-posta. */
    public String iletisimEpostasi() {
        return iletisimEpostasi;
    }

    /** Yönetim ayrı bir alan adında mı yayınlanıyor? */
    public boolean yonetimAyri() {
        return yonetimAdresi != null;
    }

    /** Yönetim alan adının ana makine adı (ör. admin.kitappla.com); ayrı değilse null. */
    public String yonetimAlanAdi() {
        return yonetimAdresi == null ? null : normalizeDomain(yonetimAdresi);
    }

    /** Yönetim sayfasına bağlantı: ayrı alan adındaysa mutlak adres, değilse site içi yol. */
    public String yonetimLinki(String yol) {
        return yonetimAdresi == null ? yol : yonetimAdresi + yol;
    }

    /** Şikâyet destek sohbetinde üyeye gösterilen karşı taraf adı (yönetici kimliği gizli kalır). */
    public String destekAdi() {
        return ad + " Destek";
    }

    /** Destek sohbetinin avatarında görünen iki harf: marka adının baş harfi + "D". */
    public String destekKisaltma() {
        return ad.substring(0, ad.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.forLanguageTag("tr")) + "D";
    }

    /** E-posta konusu: "Marka — Konu". */
    public String epostaKonusu(String konu) {
        return ad + " — " + konu;
    }
}
