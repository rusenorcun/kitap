package app.kitapla.api.v1;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** API liste uçlarında isteğe bağlı sayfalama. */
final class Sayfalama {

    /** Sayfalı yanıtta toplam kayıt sayısını taşıyan başlık. */
    static final String TOPLAM_BASLIGI = "X-Total-Count";

    private Sayfalama() {
    }

    /**
     * {@code page} verilmezse tüm liste döner: mobil uygulamanın mevcut sürümü sayfalama kullanmıyor.
     * Sayfa boyutu 1–100 aralığında tutulur.
     */
    static Pageable of(Integer page, int size) {
        return page == null ? Pageable.unpaged() : PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
    }
}
