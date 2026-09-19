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
    private final boolean document;

    public Features(@Value("${kitapla.features.shipping:false}") boolean shipping,
                    @Value("${kitapla.features.purchase:false}") boolean purchase,
                    @Value("${kitapla.features.address:false}") boolean address,
                    @Value("${kitapla.features.handover:true}") boolean handover,
                    @Value("${kitapla.features.document:false}") boolean document) {
        this.shipping = shipping;
        this.purchase = purchase;
        this.address = address;
        this.handover = handover;
        this.document = document;
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

    /**
     * Belge yükleyerek öğrenci başvurusu açık mı? Öğrenci doğrulaması okul e-postasına
     * (.edu.tr) taşındığı için kapalıdır; kod, yönetim ekranı ve sütunlar yerinde durur.
     */
    public boolean isDocument() {
        return document;
    }
}
