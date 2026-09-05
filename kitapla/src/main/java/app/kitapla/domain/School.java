package app.kitapla.domain;

/**
 * Üyenin kampüsü. Teslim yüz yüze ve kampüs içinde olduğu için kayıtta sorulur;
 * buluşma noktaları da kampüse göre listelenir.
 */
public enum School {

    ATATURK_UNIVERSITESI("Atatürk Üniversitesi"),
    ERZURUM_TEKNIK_UNIVERSITESI("Erzurum Teknik Üniversitesi");

    private final String label;

    School(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Bilinmeyen ya da boş değer için null döner; form seçimi doğrudan buradan çözülür. */
    public static School of(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return School.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
