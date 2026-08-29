package app.kitapla.domain;

/** Neyin şikâyet edildiği. */
public enum ReportKind {
    /** Sohbet (mesajlaşma) */
    CONVERSATION,
    /** Bağış ilanı */
    DONATION,
    /** Kitap isteği */
    REQUEST,
    /** Takas ilanı */
    SWAP_BOOK,
    /** Üyenin kendisi */
    USER
}
