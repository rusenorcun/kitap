package app.kitapla.domain;

/** Sohbetin bağlı olduğu alışveriş türü. */
public enum ConversationKind {
    /** Bağış talebi (Claim) */
    CLAIM,
    /** Karşılanan istek (BookRequest) */
    REQUEST,
    /** Kabul edilen takas teklifi (SwapOffer) */
    SWAP,
    /** Şikâyet destek / yönetici irtibat görüşmesi (Report) */
    REPORT
}
