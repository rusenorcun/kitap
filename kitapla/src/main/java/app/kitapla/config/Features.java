package app.kitapla.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Açılıp kapatılabilen özellikler.
 * <p>
 * Kampüs içi yüz yüze teslime geçilirken kargo ve "satın alıp gönder" akışları
 * <b>silinmedi</b>, yalnızca kapatıldı. İlgili kod, enum değerleri ve veritabanı
 * sütunları yerinde durur; ileride bu bayraklar {@code true} yapılarak akış
 * yeniden açılabilir.
 */
@Component
public class Features {

    private final boolean shipping;
    private final boolean purchase;
    private final boolean address;
    private final boolean handover;

    public Features(@Value("${kitapla.features.shipping:false}") boolean shipping,
                    @Value("${kitapla.features.purchase:false}") boolean purchase,
                    @Value("${kitapla.features.address:false}") boolean address,
                    @Value("${kitapla.features.handover:true}") boolean handover) {
        this.shipping = shipping;
        this.purchase = purchase;
        this.address = address;
        this.handover = handover;
    }

    /** Kargo akışı (kargoya verildi → teslim alındı) açık mı? */
    public boolean isShipping() {
        return shipping;
    }

    /** "Satın alıp gönderirim" kaynağı ve alışveriş linki açık mı? */
    public boolean isPurchase() {
        return purchase;
    }

    /** Profilde teslimat adresi zorunlu mu? (Yüz yüze teslimde gerekmez.) */
    public boolean isAddress() {
        return address;
    }

    /** Kampüs içi yüz yüze teslim akışı açık mı? */
    public boolean isHandover() {
        return handover;
    }
}
