package app.kitapla.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Alışveriş linkinden OpenGraph üst verisi çeker (başlık / kapak / açıklama).
 * Ayrıştırma (parse) ağdan ayrıdır; böylece birim testlerinde sabit HTML ile doğrulanır.
 */
@Service
public class OpenGraphService {

    private static final int TIMEOUT_MS = 8000;
    private static final String UA = "Mozilla/5.0 (compatible; KitaplaBot/1.0)";

    /** Sayfa HTML'inden üst veriyi ayrıştırır (ağ erişimi yok). */
    public BookMetadata parse(String html) {
        if (html == null || html.isBlank()) return new BookMetadata(null, null, null, null);
        Document doc = Jsoup.parse(html);

        String title = firstNonBlank(
                meta(doc, "og:title"),
                doc.title());
        String image = firstNonBlank(
                meta(doc, "og:image"),
                meta(doc, "og:image:secure_url"));
        String description = firstNonBlank(
                meta(doc, "og:description"),
                meta(doc, "description"));
        // Yazar standart OG'de yoktur; bazı sitelerde bulunur.
        String author = firstNonBlank(
                meta(doc, "book:author"),
                meta(doc, "author"),
                meta(doc, "og:author"));

        return new BookMetadata(clean(title), clean(author), clean(image), clean(description));
    }

    /** Linki indirip üst veriyi döndürür. Hata olursa boş sonuç döner (çağıran elle girebilsin). */
    public BookMetadata fetch(String url) {
        if (url == null || !url.matches("(?i)^https?://.+")) {
            return new BookMetadata(null, null, null, null);
        }
        try {
            String html = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .get()
                    .outerHtml();
            return parse(html);
        } catch (IOException | IllegalArgumentException e) {
            return new BookMetadata(null, null, null, null);
        }
    }

    private static String meta(Document doc, String prop) {
        String v = doc.select("meta[property=" + prop + "]").attr("content");
        if (v == null || v.isBlank()) v = doc.select("meta[name=" + prop + "]").attr("content");
        return v;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
