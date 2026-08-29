package app.kitapla.domain;

/**
 * MATCHED  : talep oluştu
 * ARRANGED : yüz yüze buluşma ayarlandı (kampüs teslimi)
 * SHIPPED  : kargoya verildi (kargo akışı kapalıyken kullanılmaz)
 * DELIVERED: teslim edildi — her iki akışın da bitiş durumu
 * NO_SHOW  : buluşmaya gelinmedi; kitap havuza döner ama kota hakkı yanar
 */
public enum ClaimStatus { MATCHED, ARRANGED, SHIPPED, DELIVERED, NO_SHOW }
