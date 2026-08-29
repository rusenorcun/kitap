package app.kitapla.domain;

/** Şikâyet gerekçesi. Etiketler kullanıcıya olduğu gibi gösterilir. */
public enum ReportReason {
    TACIZ("Taciz, hakaret ya da tehdit"),
    UYGUNSUZ("Uygunsuz içerik"),
    SPAM("Spam ya da reklam"),
    SAHTE("Sahte ilan ya da yanıltıcı bilgi"),
    GELMEDI("Buluşmaya gelmedi"),
    TICARET("Satış ya da ticari amaç"),
    DIGER("Diğer");

    private final String etiket;

    ReportReason(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }
}
