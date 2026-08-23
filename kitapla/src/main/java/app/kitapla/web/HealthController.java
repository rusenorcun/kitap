package app.kitapla.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Konteyner sağlık kontrolü için asgari uç.
 * <p>
 * Bilerek sadece "ayakta mı" bilgisini verir: sürüm, bağımlılık listesi ya da
 * yapılandırma sızdırmaz. Veritabanına erişilemiyorsa 503 döner, böylece
 * Docker sağlıksız konteyneri fark eder.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/saglik")
    public ResponseEntity<String> saglik() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok("iyi");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("veritabani yok");
        }
    }
}
