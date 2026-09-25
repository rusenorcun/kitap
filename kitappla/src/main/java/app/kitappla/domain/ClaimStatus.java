package app.kitappla.domain;

import java.util.Set;

/**
 * MATCHED  : talep oluştu
 * ARRANGED : yüz yüze buluşma ayarlandı (kampüs teslimi)
 * SHIPPED  : kargoya verildi (kargo akışı kapalıyken kullanılmaz)
 * DELIVERED: teslim edildi — her iki akışın da bitiş durumu
 * NO_SHOW  : buluşmaya gelinmedi; kitap havuza döner ama kota hakkı yanar
 * CANCELLED: taraflardan biri askıya alındı; kitap havuza döner, alıcının kota hakkı iade edilir
 */
public enum ClaimStatus {
    MATCHED, ARRANGED, SHIPPED, DELIVERED, NO_SHOW, CANCELLED;

    /** Bağış adedini tutmayan durumlar: kitap yeniden başkasına verilebilir. */
    public static final Set<ClaimStatus> ADET_TUTMAYAN = Set.of(NO_SHOW, CANCELLED);
}
