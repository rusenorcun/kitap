package app.kitappla.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alışveriş ve kitap linklerinden OpenGraph, JSON-LD ve mikroformat üst verisi çeker.
 * Başlık, yazar, yüksek çözünürlüklü kapak görseli ve kitap açıklamasını ayrıştırır.
 */
@Service
public class OpenGraphService {

    private static final int TIMEOUT_MS = 8000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private static final int MAX_BYTES = 512 * 1024; // 512 KB
    private static final int MAX_REDIRECTS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Pattern SITE_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s*(?:[-–—:|/]|\\|)\\s*(?:Kitapyurdu\\.com|Kitapyurdu|D&R|İdefix|idefix|BKM Kitap|Bkmkitap|" +
                    "Amazon\\.com\\.tr|Amazon|Hepsiburada|Trendyol|Kidega|İlknokta|Pandora|KitapSepeti|" +
                    "Kitap365|Babil|Kırmızı Kedi|Can Yayınları Store|Kitap)\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static final Pattern BUY_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s*(?:[-–—:|/]|\\|)?\\s*(?:Fiyatı,\\s*Yorumları,\\s*Satın Al|Kitabı ve Fiyatı|Fiyatı|Satın Al|Yorumları|Online Satış|En Uygun Fiyatlarla)\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static final Pattern DESC_PREFIX_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:[\\p{L}\\d\\s]{1,40}\\s+)?(?:Kitap\\s+)?(?:Açıklaması|Açıklama|Tanıtım\\s+Bülteni|Tanıtım\\s+Yazısı|Arka\\s+Kapak\\s+Yazısı|Arka\\s+Kapak|Ürün\\s+Açıklaması|Ürün\\s+Bilgisi|Özet)\\s*[:：\\-–—]?\\s*",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Sayfa HTML'inden üst veriyi ayrıştırır (ağ erişimi yok). */
    public BookMetadata parse(String html) {
        return parse(html, null);
    }

    /** Sayfa HTML'inden üst veriyi ayrıştırır ve göreceli URL'leri baseUri ile tamamlar. */
    public BookMetadata parse(String html, String baseUri) {
        if (html == null || html.isBlank()) return new BookMetadata(null, null, null, null);
        Document doc = (baseUri != null && !baseUri.isBlank()) ? Jsoup.parse(html, baseUri) : Jsoup.parse(html);

        // 1. JSON-LD verilerini topla
        JsonLdData ld = parseJsonLd(doc);

        // 2. Yazar tespiti (JSON-LD -> Meta -> HTML seçicileri)
        String author = firstNonBlank(
                ld.author,
                meta(doc, "book:author"),
                meta(doc, "books:author"),
                meta(doc, "author"),
                meta(doc, "og:author"),
                meta(doc, "twitter:creator"),
                meta(doc, "citation_author"),
                meta(doc, "DC.creator"),
                meta(doc, "dc.creator")
        );

        if (author == null) {
            author = findAuthorFromDom(doc);
        }

        // 3. Ham başlık tespiti
        String rawTitle = firstNonBlank(
                meta(doc, "og:title"),
                meta(doc, "twitter:title"),
                meta(doc, "title"),
                ld.title,
                doc.title()
        );

        // 4. Başlık ve Yazar ayrıştırma / temizleme
        TitleAndAuthor parsed = extractTitleAndAuthor(rawTitle, author);
        String finalTitle = parsed.title;
        String finalAuthor = parsed.author;

        // 5. Kapak görseli tespiti
        String image = findCoverImage(doc, ld.image, baseUri);

        // 6. Kitap açıklaması tespiti
        String description = findDescription(doc, ld.description, finalTitle, finalAuthor);

        return new BookMetadata(clean(finalTitle, 300), clean(finalAuthor, 200), clean(image, 1000), clean(description, 1000));
    }

    /** Linki indirip üst veriyi döndürür. SSRF koruması, güvenli yönlendirme ve boyut sınırı içerir. */
    public BookMetadata fetch(String url) {
        if (url == null || !url.matches("(?i)^https?://.+")) {
            return new BookMetadata(null, null, null, null);
        }

        try {
            URI currentUri = URI.create(url.trim());
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
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                conn.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7");

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

                    URI nextUri = currentUri.resolve(location.trim());
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
                    String tLower = type.toLowerCase(Locale.ROOT);
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
                return parse(html, currentUri.toString());
            }
        } catch (Exception e) {
            return new BookMetadata(null, null, null, null);
        }

        return new BookMetadata(null, null, null, null);
    }

    private static class JsonLdData {
        String title;
        String author;
        String image;
        String description;
    }

    private JsonLdData parseJsonLd(Document doc) {
        JsonLdData res = new JsonLdData();
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element s : scripts) {
            String json = s.data();
            if (json == null || json.isBlank()) continue;
            try {
                JsonNode root = JSON.readTree(json);
                extractFromJsonNode(root, res);
            } catch (Exception ignored) {
            }
        }
        return res;
    }

    private void extractFromJsonNode(JsonNode node, JsonLdData res) {
        if (node == null || node.isNull()) return;

        if (node.isArray()) {
            for (JsonNode item : node) {
                extractFromJsonNode(item, res);
            }
            return;
        }

        if (node.isObject()) {
            if (node.has("@graph")) {
                extractFromJsonNode(node.get("@graph"), res);
            }

            String type = node.has("@type") ? node.get("@type").asText("") : "";

            // Başlık
            if (res.title == null && (type.equalsIgnoreCase("Book") || type.equalsIgnoreCase("Product") || type.equalsIgnoreCase("IndividualProduct"))) {
                if (node.has("name") && !node.get("name").asText().isBlank()) {
                    res.title = node.get("name").asText().trim();
                }
            }

            // Yazar
            if (res.author == null) {
                if (node.has("author")) {
                    res.author = parsePersonOrOrganization(node.get("author"));
                } else if (node.has("creator")) {
                    res.author = parsePersonOrOrganization(node.get("creator"));
                } else if (node.has("byArtist")) {
                    res.author = parsePersonOrOrganization(node.get("byArtist"));
                } else if (type.equalsIgnoreCase("Book") && node.has("brand")) {
                    res.author = parsePersonOrOrganization(node.get("brand"));
                }
            }

            // Görsel
            if (res.image == null) {
                if (node.has("image")) {
                    JsonNode img = node.get("image");
                    if (img.isTextual() && !img.asText().isBlank()) {
                        res.image = img.asText().trim();
                    } else if (img.isArray() && !img.isEmpty()) {
                        JsonNode first = img.get(0);
                        if (first.isTextual()) res.image = first.asText().trim();
                        else if (first.has("url")) res.image = first.get("url").asText().trim();
                    } else if (img.isObject() && img.has("url")) {
                        res.image = img.get("url").asText().trim();
                    }
                }
            }

            // Açıklama
            if (res.description == null && node.has("description")) {
                String d = node.get("description").asText("");
                if (!d.isBlank()) {
                    res.description = d.trim();
                }
            }
        }
    }

    private String parsePersonOrOrganization(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) {
            String t = node.asText().trim();
            return t.isEmpty() ? null : t;
        }
        if (node.isArray() && !node.isEmpty()) {
            return parsePersonOrOrganization(node.get(0));
        }
        if (node.isObject() && node.has("name")) {
            String name = node.get("name").asText().trim();
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    private String findAuthorFromDom(Document doc) {
        String author = firstNonBlank(
                text(doc, "[itemprop=author] [itemprop=name]"),
                text(doc, "[itemprop=author]"),
                text(doc, "[itemprop=creator]"),
                text(doc, ".ky-pd-heading__author"),
                text(doc, "a.ky-pd-heading__author"),
                text(doc, ".pr_author a"),
                text(doc, ".authors-wrapper a"),
                text(doc, ".js-wrapper-author a"),
                text(doc, "h2.author a"),
                text(doc, ".author.seo-heading a"),
                text(doc, ".book-author a"),
                text(doc, ".product-author a"),
                text(doc, ".yazar a"),
                text(doc, "a[href*='/yazar/']"),
                text(doc, "a[href*='/author/']"),
                text(doc, ".book-author"),
                text(doc, ".product-author")
        );

        if (author != null) {
            author = author.replaceAll("(?i)^yazar\\s*:\\s*", "").trim();
            if (author.length() > 200) author = null;
        }
        return author;
    }

    private static class TitleAndAuthor {
        final String title;
        final String author;

        TitleAndAuthor(String title, String author) {
            this.title = title;
            this.author = author;
        }
    }

    private TitleAndAuthor extractTitleAndAuthor(String rawTitle, String existingAuthor) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return new TitleAndAuthor(null, existingAuthor);
        }

        String t = rawTitle.trim();
        // HTML entity çöz
        t = Jsoup.parse(t).text().trim();

        // 1. Bilinen site eklerini temizle (örn. " - Kitapyurdu.com", " | D&R")
        boolean changed = true;
        while (changed) {
            String before = t;
            t = SITE_SUFFIX_PATTERN.matcher(t).replaceFirst("").trim();
            t = BUY_SUFFIX_PATTERN.matcher(t).replaceFirst("").trim();
            changed = !t.equals(before);
        }

        String author = existingAuthor;

        // 2. Parantez içi yazar deseni: "Kitap Adı (Yazar Adı)"
        Matcher parenMatcher = Pattern.compile("^(.+?)\\s*\\(([^)]+)\\)\\s*$").matcher(t);
        if (parenMatcher.find()) {
            String titlePart = parenMatcher.group(1).trim();
            String parenContent = parenMatcher.group(2).trim();
            // Parantez içi baskı/cilt/sayfa bilgisi değil de yazar ismi gibi ise
            if (!parenContent.matches("(?i).*(cilt|baskı|basım|özel|kutulu|cep|sayfa|boy|kitap|yayın).*")
                    && parenContent.split("\\s+").length <= 4) {
                if (author == null || author.isBlank()) {
                    author = parenContent;
                }
                t = titlePart;
            }
        }

        // 3. Başlık parçalarını ayrıştır (- / | : vb.)
        String[] parts = t.split("\\s*(?:[-–—|/]|\\|)\\s*");
        if (parts.length >= 2) {
            String p0 = parts[0].trim();
            String p1 = parts[1].trim();

            if (author == null || author.isBlank()) {
                // p1 yazar adayı olabilir mi?
                if (p1.split("\\s+").length <= 4 && !p1.matches("(?i).*(satın|fiyat|yorum|roman|yayın|baskı|dizi|kitap).*")) {
                    author = p1;
                    t = p0;
                }
            } else {
                // Yazar zaten biliniyorsa, başlıktan yazar ve yayınevi parçalarını temizle
                if (p1.equalsIgnoreCase(author) || p1.toLowerCase(Locale.ROOT).contains(author.toLowerCase(Locale.ROOT))
                        || p1.matches("(?i).*(yayın|yayınevi|kitap).*")) {
                    t = p0;
                }
            }
        }

        // Yazar biliniyorsa başlıktan yazar adını temizle
        if (author != null && !author.isBlank()) {
            String authorClean = Pattern.quote(author);
            t = t.replaceAll("(?i)\\s*[-–—|:/,]\\s*" + authorClean, "").trim();
            t = t.replaceAll("(?i)" + authorClean + "\\s*[-–—|:/,]\\s*", "").trim();
            t = t.replaceAll("(?i)\\(" + authorClean + "\\)", "").trim();
        }

        // Yayınevi parçası kaldıysa temizle (örn. "| Can Yayınları")
        t = t.replaceAll("(?i)\\s*[-–—|:/,]\\s*[^\\-]+?\\s*(?:yayınları|yayınevi|yayıncılık)\\s*$", "").trim();

        // Başlıktan son kalan tekil tire vb. temizle
        t = t.replaceAll("^[\\s-–—|:/,]+|[\\s-–—|:/,]+$", "").trim();

        return new TitleAndAuthor(t.isEmpty() ? rawTitle.trim() : t, author);
    }

    private String findCoverImage(Document doc, String ldImage, String baseUri) {
        String raw = firstNonBlank(
                // 1. Kitapyurdu özel kapak görseli (yüksek çözünürlük)
                attr(doc, "a.js-jbox-book-cover", "href"),
                attr(doc, "img#js-book-cover", "src"),
                attr(doc, ".ky-pd-cover-book img", "src"),
                // 2. D&R özel kapak görseli
                attr(doc, "img.js-prd-first-image", "src"),
                attr(doc, "img.js-product-image", "src"),
                attr(doc, ".product-image img", "src"),
                // 3. JSON-LD görseli
                ldImage,
                // 4. HTML genel ürün görseli
                attr(doc, "img[itemprop=image]", "src"),
                attr(doc, "#landingImage", "src"),
                attr(doc, "#imgBlkFront", "src"),
                attr(doc, "#main-product-img", "src"),
                // 5. OpenGraph ve Meta
                meta(doc, "og:image"),
                meta(doc, "og:image:secure_url"),
                meta(doc, "og:image:url"),
                meta(doc, "twitter:image"),
                meta(doc, "twitter:image:src"),
                attr(doc, "link[rel=image_src]", "href")
        );

        return normalizeImageUrl(raw, baseUri);
    }

    private String normalizeImageUrl(String imgUrl, String baseUri) {
        if (imgUrl == null || imgUrl.isBlank()) return null;
        String s = imgUrl.trim();

        // 1x1 piksel, placeholder, logo filtreleme
        if (s.contains("data:image") && s.length() < 200) return null;
        if (s.matches("(?i).*(spacer|blank|pixel|1x1|favicon).*")) return null;

        // Protocol-relative URL
        if (s.startsWith("//")) {
            s = "https:" + s;
        } else if (!s.matches("(?i)^https?://.*") && baseUri != null && !baseUri.isBlank()) {
            try {
                s = URI.create(baseUri.trim()).resolve(s).toString();
            } catch (Exception ignored) {
            }
        }

        // Kitapyurdu küçük thumbnail yükseltme (/miw:200/mih:200 -> /wi:800/)
        if (s.contains("img.kitapyurdu.com") && s.contains("/miw:200/mih:200")) {
            s = s.replace("/miw:200/mih:200", "/wi:800");
        }
        // D&R küçük thumbnail yükseltme (/64x64-0/ veya /500x400-0/ -> /600x600-0/)
        if (s.contains("dr.com.tr") && (s.contains("/64x64-0/") || s.contains("/500x400-0/"))) {
            s = s.replace("/64x64-0/", "/600x600-0/").replace("/500x400-0/", "/600x600-0/");
        }

        return s;
    }

    private String findDescription(Document doc, String ldDescription, String title, String author) {
        String raw = firstNonBlank(
                ldDescription,
                cleanElementText(doc, "[itemprop=description]"),
                cleanElementText(doc, ".ky-pd-about__description"),
                cleanElementText(doc, ".pr_description"),
                cleanElementText(doc, "#tab-description"),
                cleanElementText(doc, ".product-description-body"),
                cleanElementText(doc, ".product-description"),
                cleanElementText(doc, ".tanitim-bulteni"),
                cleanElementText(doc, "#tanitim-bulteni"),
                cleanElementText(doc, ".arka-kapak"),
                cleanElementText(doc, ".book-description"),
                meta(doc, "og:description"),
                meta(doc, "description"),
                meta(doc, "twitter:description")
        );

        if (raw == null || raw.isBlank()) return null;

        String desc = Jsoup.parse(raw).text().trim();

        // Kitapyurdu og:description kalıbı: "Başlık - Yayınevi - Yazar - Gerçek Açıklama..."
        if (desc.contains(" - ")) {
            String[] parts = desc.split(" - ", 4);
            if (parts.length >= 4) {
                // Son kısım gerçek açıklamadır
                desc = parts[3].trim();
            }
        }

        // Açıklamanın başındaki "Simyacı Kitap Açıklaması", "Kitap Açıklaması:", "Arka Kapak Yazısı:" vb. etiketleri temizle
        boolean cleaned = true;
        while (cleaned) {
            String before = desc;
            if (title != null && !title.isBlank()) {
                String titleRegex = "(?i)^\\s*" + Pattern.quote(title) + "\\s+(?:Kitap\\s+)?(?:Açıklaması|Açıklama|Tanıtım\\s+Bülteni|Tanıtım\\s+Yazısı|Arka\\s+Kapak|Özet)\\s*[:：\\-–—]?\\s*";
                desc = desc.replaceFirst(titleRegex, "").trim();
            }
            desc = DESC_PREFIX_PATTERN.matcher(desc).replaceFirst("").trim();
            cleaned = !desc.equals(before);
        }

        // Satış sloganlarını temizle (örn. "Simyacı en cazip fiyat ile D&R'de...")
        desc = desc.replaceAll("(?i)\\s*(?:en cazip fiyat ile D&R'de|Keşfetmek için hemen tıklayınız!?).*$", "").trim();

        return desc.isEmpty() ? null : desc;
    }

    private static String cleanElementText(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        if (el == null) return null;
        Element clone = el.clone();
        clone.select("h1, h2, h3, h4, .product-description-header, .standart-h2-title, .section-cover, .dr-tab-detail-header-btn").remove();
        String val = clone.text();
        return val == null || val.isBlank() ? null : val.trim();
    }

    private static String meta(Document doc, String prop) {
        String v = doc.select("meta[property=" + prop + "]").attr("content");
        if (v == null || v.isBlank()) v = doc.select("meta[name=" + prop + "]").attr("content");
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String attr(Document doc, String selector, String attribute) {
        Element el = doc.selectFirst(selector);
        if (el == null) return null;
        String val = el.attr(attribute);
        return val == null || val.isBlank() ? null : val.trim();
    }

    private static String text(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        if (el == null) return null;
        String val = el.text();
        return val == null || val.isBlank() ? null : val.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static java.nio.charset.Charset extractCharset(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
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
}
