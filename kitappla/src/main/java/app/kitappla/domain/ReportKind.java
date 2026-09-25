package app.kitappla.domain;

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

    public static ReportKind of(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        try {
            return ReportKind.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "DELIVERY" -> CLAIM;
                case "BOOK_REQUEST" -> REQUEST;
                case "SWAPBOOK" -> SWAP_BOOK;
                case "SWAPOFFER", "SWAP" -> SWAP_OFFER;
                default -> null;
            };
        }
    }
}
