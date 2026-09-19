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
    REPORT,
    /** Üyenin yönetime doğrudan yazdığı genel destek sohbeti (refId: üyenin kimliği) */
    SUPPORT;

    /** Karşı tarafı tek bir kişi değil, yönetimin bütünü olan sohbetler. */
    public boolean yonetimSohbeti() {
        return this == REPORT || this == SUPPORT;
    }
}
