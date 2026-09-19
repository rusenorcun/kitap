package app.kitapla.service;

/**
 * Kullanıcı metinlerini sütun sınırına uydurur. Web formları {@code maxlength} koysa da API
 * ve elle gönderilen istekler bunu atlayabilir; taşan metin veritabanı hatasıyla işlemi 500'e
 * düşürüyordu. Ayrıca bir alandan diğerine taşınan metin (bağış açıklaması 500 → takas notu
 * 300) da burada kırpılır.
 */
final class Metin {

    private Metin() {
    }

    /** Boşsa null; değilse kırpılmış ve en fazla {@code max} karakter. */
    static String kisalt(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
