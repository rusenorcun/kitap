package app.kitapla.domain;

/**
 * OPEN     : karşılanmayı bekliyor
 * FULFILLED: biri karşılamayı üstlendi
 * ARRANGED : yüz yüze buluşma ayarlandı (kampüs teslimi)
 * SHIPPED  : kargoya verildi (kargo akışı kapalıyken kullanılmaz)
 * DELIVERED: teslim edildi — her iki akışın da bitiş durumu
 * NO_SHOW  : buluşmaya gelinmedi
 */
public enum RequestStatus { OPEN, FULFILLED, ARRANGED, SHIPPED, DELIVERED, NO_SHOW }
