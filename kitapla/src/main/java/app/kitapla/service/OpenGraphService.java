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

    private static final int MAX_BYTES = 512 * 1024; // 512 KB
    private static final int MAX_REDIRECTS = 3;

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

    /** Linki indirip üst veriyi döndürür. SSRF koruması, güvenli yönlendirme ve boyut sınırı içerir. */
    public BookMetadata fetch(String url) {
        if (url == null || !url.matches("(?i)^https?://.+")) {
            return new BookMetadata(null, null, null, null);
        }

        try {
            java.net.URI currentUri = java.net.URI.create(url.trim());
            java.util.Set<String> visited = new java.util.HashSet<>();

            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                if (!SsrfValidator.isSafeUri(currentUri)) {
                    return new BookMetadata(null, null, null, null);
                }

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) currentUri.toURL().openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");

                int code;
                try {
                    code = conn.getResponseCode();
                } catch (Exception e) {
                    conn.disconnect();
                    return new BookMetadata(null, null, null, null);
                }

                // Yönlendirmeleri her adımda IP kontrolü yaparak güvenle takip et
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null || location.isBlank()) {
                        return new BookMetadata(null, null, null, null);
                    }

                    java.net.URI nextUri = currentUri.resolve(location.trim());
                    if (!visited.add(nextUri.toString())) {
                        // Yönlendirme döngüsü
                        return new BookMetadata(null, null, null, null);
                    }
                    currentUri = nextUri;
                    continue;
                }

                if (code != 200) {
                    conn.disconnect();
                    return new BookMetadata(null, null, null, null);
                }

                // Content-Type kontrolü: sadece HTML içerikleri kabul et
                String type = conn.getContentType();
                if (type != null) {
                    String tLower = type.toLowerCase(java.util.Locale.ROOT);
                    int semi = tLower.indexOf(';');
                    if (semi > 0) tLower = tLower.substring(0, semi).trim();
                    if (!tLower.contains("text/html") && !tLower.contains("application/xhtml+xml")) {
                        conn.disconnect();
                        return new BookMetadata(null, null, null, null);
                    }
                }

                java.nio.charset.Charset charset = extractCharset(type);
                byte[] data;
                try (java.io.InputStream in = conn.getInputStream()) {
                    data = readLimited(in, MAX_BYTES);
                } finally {
                    conn.disconnect();
                }

                if (data == null || data.length == 0) {
                    return new BookMetadata(null, null, null, null);
                }

                String html = new String(data, charset);
                return parse(html);
            }
        } catch (Exception e) {
            return new BookMetadata(null, null, null, null);
        }

        return new BookMetadata(null, null, null, null);
    }

    private static java.nio.charset.Charset extractCharset(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(java.util.Locale.ROOT);
            int idx = lower.indexOf("charset=");
            if (idx >= 0) {
                String cs = contentType.substring(idx + 8).trim();
                int end = cs.indexOf(';');
                if (end >= 0) cs = cs.substring(0, end).trim();
                cs = cs.replace("\"", "").replace("'", "");
                try {
                    return java.nio.charset.Charset.forName(cs);
                } catch (Exception ignored) {
                }
            }
        }
        return java.nio.charset.StandardCharsets.UTF_8;
    }

    private static byte[] readLimited(java.io.InputStream in, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (total + n > maxBytes) {
                int take = maxBytes - total;
                if (take > 0) {
                    out.write(buffer, 0, take);
                }
                break;
            }
            out.write(buffer, 0, n);
            total += n;
        }
        return out.toByteArray();
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
