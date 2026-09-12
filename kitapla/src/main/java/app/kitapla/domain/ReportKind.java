package app.kitapla.domain;

/**
 * Neyin şikâyet edildiği.
 * <p>
 * {@code etiket} listelerde ve rozetlerde gösterilen yalın addır. Bildirim
 * cümlesindeki çekimli biçim ("... bir takas sürecini şikâyet etti") ayrıdır;
 * bkz. {@code ReportService.turAdi}.
 */
public enum ReportKind {
    /** Sohbet (mesajlaşma) */
    CONVERSATION("Sohbet"),
    /** Bağış ilanı */
    DONATION("Bağış ilanı"),
    /** Kitap isteği (veya karşılanan istek teslimatı) */
    REQUEST("İstek"),
    /** Bağış talebi / teslimatı (Claim) */
    CLAIM("Bağış teslimatı"),
    /** Takas ilanı */
    SWAP_BOOK("Takas ilanı"),
    /** Takas teklifi / süreci (SwapOffer) */
    SWAP_OFFER("Takas süreci"),
    /** Üyenin kendisi */
    USER("Üye");

    private final String etiket;

    ReportKind(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }
}
