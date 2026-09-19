package app.kitapla.service;

/**
 * Üye askıya alındığında karşı tarafa giden bildirimin tek kaynağı; tüm süreçlerde
 * aynı dili kullanır: "Karşı taraf askıya alındığı için buluşma iptal edildi ve hakkınız iade edildi."
 */
final class AskiBildirimi {

    static final String TUR = "askiya_alma";

    private AskiBildirimi() {
    }

    /**
     * @param kitap        kitabın adı
     * @param bulusmaVardi buluşma ayarlanmışsa "buluşma", değilse {@code surec} iptal edildi denir
     * @param surec        buluşma yoksa iptal edilen şey (talep, istek, takas teklifi…)
     * @param hakIade      karşı tarafa bir hak (kota, bağış adedi, ilan) geri verildiyse true
     * @param ek           hangi hakkın döndüğünü anlatan isteğe bağlı cümle
     */
    static String metin(String kitap, boolean bulusmaVardi, String surec, boolean hakIade, String ek) {
        StringBuilder s = new StringBuilder("\"").append(kitap).append("\": Karşı taraf askıya alındığı için ")
                .append(bulusmaVardi ? "buluşma" : surec).append(" iptal edildi");
        s.append(hakIade ? " ve hakkınız iade edildi." : ".");
        if (ek != null && !ek.isBlank()) s.append(' ').append(ek);
        return s.toString();
    }
}
