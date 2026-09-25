package app.kitappla.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Android App Links doğrulama dosyası. Uygulama manifesti {@code https://kitappla.com} ve
 * {@code https://www.kitappla.com} bağlantılarını {@code autoVerify} ile ister; Android bu dosyayı iki alan adından da
 * <b>yönlendirmesiz</b> 200 ile almazsa bağlantılar uygulama yerine tarayıcıda açılır (Caddy'de kök alan istisnası:
 * {@code deploy/kitappla.caddy}).
 * <p>
 * Parmak izleri {@code kitappla.android.sha256-sertifikalar} (virgülle ayrılmış) ayarından gelir. Uygulama Google Play'e
 * "Play App Signing" ile çıkarsa Play Console'daki imzalama anahtarının SHA-256'sı da eklenmelidir.
 */
@RestController
public class AssetLinksController {

    private static final String HEX_CIFTI = "^([0-9A-F]{2}:){31}[0-9A-F]{2}$";

    private final List<Map<String, Object>> govde;

    public AssetLinksController(@Value("${kitappla.android.package}") String paket,
                                @Value("${kitappla.android.sha256-sertifikalar}") String parmakIzleri) {
        List<String> izler = Arrays.stream(parmakIzleri.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
        for (String iz : izler) {
            if (!iz.matches(HEX_CIFTI))
                throw new IllegalStateException("Geçersiz Android sertifika parmak izi (SHA-256, AA:BB:… biçiminde olmalı): " + iz);
        }
        this.govde = List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", paket.trim(),
                        "sha256_cert_fingerprints", izler)));
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(govde);
    }
}
