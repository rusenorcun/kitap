package app.kitapla.service;

import java.util.Locale;

/** Liste aramalarında ortak yardımcı. */
final class Arama {

    private Arama() {
    }

    /**
     * Kitap adı ya da yazarında geçen metin için küçük harfli LIKE deseni.
     * Arama yoksa "%" döner (her şey eşleşir); kullanıcının yazdığı % ve _ harf olarak aranır.
     */
    static String desen(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String kacisli = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + kacisli + "%";
    }
}
