package app.kitapla.web;

import app.kitapla.config.Marka;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Marka nesnesini TÜM şablon render'larına ekler: web sayfaları, hata sayfaları
 * ve Boot'un hata görünümü dâhil. Paket kısıtlaması yoktur; yalnızca ucuz bir
 * nesne referansı koyar, veritabanına dokunmaz.
 */
@ControllerAdvice
public class MarkaAdvice {

    private final Marka marka;

    public MarkaAdvice(Marka marka) {
        this.marka = marka;
    }

    @ModelAttribute("marka")
    public Marka marka() {
        return marka;
    }

    /** Sayfa yönetim alan adında mı sunuluyor? Menü buna göre üye bağlantılarını gizler. */
    @ModelAttribute("yonetimAlani")
    public boolean yonetimAlani(jakarta.servlet.http.HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(YonetimAlanAdiFiltresi.YONETIM_ALANI));
    }
}
