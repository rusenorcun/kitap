package app.kitapla.domain;

/**
 * MATCHED  : talep oluştu
 * ARRANGED : yüz yüze buluşma ayarlandı (kampüs teslimi)
 * SHIPPED  : kargoya verildi (kargo akışı kapalıyken kullanılmaz)
 * DELIVERED: teslim edildi — her iki akışın da bitiş durumu
 */
public enum ClaimStatus { MATCHED, ARRANGED, SHIPPED, DELIVERED }
