package app.kitapla.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Kitap kapağı görselleri. Kapak ya üye tarafından yüklenir ya da alışveriş linkinden
 * gelen adresten indirilir; her iki durumda da dosya <b>bizde</b> durur ve
 * {@code /uploads/covers/...} altından sunulur.
 *
 * <p>Uzak adresi olduğu gibi saklamak yerine indirmenin sebebi: birçok satış sitesi
 * görseli dışarıdan çağırınca vermiyor (referer kontrolü) ya da http üzerinden
 * sunuyor — https sayfada tarayıcı bunu engelliyor. İkisinde de kapak görünmüyor.</p>
 */
@Service
public class CoverService {

    private static final Logger log = LoggerFactory.getLogger(CoverService.class);

    /** Kapak için üst sınır; bundan büyüğü ne yüklenir ne indirilir. */
    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final int TIMEOUT_MS = 8000;
    private static final String UA = "Mozilla/5.0 (compatible; KitaplaBot/1.0)";

    private static final Map<String, String> UZANTI = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final Path dir;

    public CoverService(@Value("${kitapla.upload-dir}") String uploadDir) {
        this.dir = Path.of(uploadDir, "covers");
    }

    /**
     * Kapağı belirler: yüklenen dosya varsa o kazanır, yoksa linkten gelen adres indirilir.
     *
     * @return şablonun kullanacağı adres; hiçbiri yoksa null
     */
    public String resolve(MultipartFile uploaded, String remoteUrl) {
        String local = saveUpload(uploaded);
        if (local != null) return local;
        return saveFromUrl(remoteUrl);
    }

    /** Üyenin yüklediği görseli saklar. Görsel değilse ya da çok büyükse null döner. */
    public String saveUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!UZANTI.containsKey(type)) {
            throw new IllegalArgumentException("Kapak görseli JPG, PNG, WEBP ya da GIF olmalı.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Kapak görseli en fazla 3 MB olabilir.");
        }
        try {
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + UZANTI.get(type);
            file.transferTo(dir.resolve(name).toAbsolutePath());
            return "/uploads/covers/" + name;
        } catch (IOException e) {
            throw new IllegalArgumentException("Kapak görseli kaydedilemedi.");
        }
    }

    /**
     * Uzak adresteki görseli indirip saklar. Başarısız olursa <b>adresin kendisini</b> döndürür;
     * kapak hiç olmamasındansa doğrudan bağlanmayı denemek daha iyi.
     */
    public String saveFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("/uploads/covers/")) return url;       // zaten bizde
        if (!url.matches("(?i)^https?://.+")) return null;

        try {
            URI currentUri = URI.create(url.trim());
            java.util.Set<String> visited = new java.util.HashSet<>();

            for (int redirect = 0; redirect <= 3; redirect++) {
                if (!SsrfValidator.isSafeUri(currentUri)) {
                    return url;
                }

                HttpURLConnection c = (HttpURLConnection) currentUri.toURL().openConnection();
                c.setRequestProperty("User-Agent", UA);
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setInstanceFollowRedirects(false);

                int code;
                try {
                    code = c.getResponseCode();
                } catch (Exception e) {
                    c.disconnect();
                    return url;
                }

                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    c.disconnect();
                    if (location == null || location.isBlank()) {
                        return url;
                    }
                    URI nextUri = currentUri.resolve(location.trim());
                    if (!visited.add(nextUri.toString())) {
                        return url;
                    }
                    currentUri = nextUri;
                    continue;
                }

                String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
                int noktaliVirgul = type.indexOf(';');
                if (noktaliVirgul > 0) type = type.substring(0, noktaliVirgul).trim();
                if (code != 200 || !UZANTI.containsKey(type)) {
                    c.disconnect();
                    return url;
                }

                Files.createDirectories(dir);
                String name = UUID.randomUUID() + UZANTI.get(type);
                Path hedef = dir.resolve(name);
                long yazilan;
                try (InputStream in = c.getInputStream()) {
                    yazilan = Files.copy(sinirli(in), hedef);
                } finally {
                    c.disconnect();
                }
                if (yazilan <= 0 || yazilan >= MAX_BYTES) {
                    Files.deleteIfExists(hedef);
                    return url;
                }
                return "/uploads/covers/" + name;
            }
            return url;
        } catch (Exception e) {
            log.debug("Kapak indirilemedi: {}", url, e);
            return url;
        }
    }

    /** Boyut sınırını aşan gövdeyi okumayı bırakır; kötü niyetli/dev dosyaya karşı. */
    private static InputStream sinirli(InputStream in) {
        return new InputStream() {
            private long okunan;

            @Override
            public int read() throws IOException {
                if (okunan >= MAX_BYTES) return -1;
                int b = in.read();
                if (b >= 0) okunan++;
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                if (okunan >= MAX_BYTES) return -1;
                int kalan = (int) Math.min(len, MAX_BYTES - okunan);
                int n = in.read(buf, off, kalan);
                if (n > 0) okunan += n;
                return n;
            }
        };
    }
}
