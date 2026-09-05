package app.kitapla.domain;

/** Neyin şikâyet edildiği. */
public enum ReportKind {
    /** Sohbet (mesajlaşma) */
    CONVERSATION,
    /** Bağış ilanı */
    DONATION,
    /** Kitap isteği (veya karşılanan istek teslimatı) */
    REQUEST,
    /** Bağış talebi / teslimatı (Claim) */
    CLAIM,
    /** Takas ilanı */
    SWAP_BOOK,
    /** Takas teklifi / süreci (SwapOffer) */
    SWAP_OFFER,
    /** Üyenin kendisi */
    USER
}
