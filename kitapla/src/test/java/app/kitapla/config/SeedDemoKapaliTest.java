package app.kitapla.config;

import app.kitapla.repo.BookRepository;
import app.kitapla.repo.PickupPointRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * kitapla.seed.demo=false iken yalnızca yönetici hesabı oluşur.
 * Herkese açık kurulumda örnek hesapların (bilinen şifreli) açılmaması için.
 */
@SpringBootTest(properties = "kitapla.seed.demo=false")
@ActiveProfiles("test")
class SeedDemoKapaliTest {

    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PickupPointRepository points;

    @Value("${kitapla.admin.email}") String adminEmail;

    @Test
    void yalnizcaYoneticiOlusur() {
        var yonetici = users.findByEmail(adminEmail);
        assertThat(yonetici).isPresent();
        assertThat(yonetici.get().isAdmin()).isTrue();

        // Örnek üyeler açılmamalı
        assertThat(users.findByEmail("ayse@ornek.com")).isEmpty();
        assertThat(users.findByEmail("elif@ornek.com")).isEmpty();
        assertThat(users.findByEmail("mert@ornek.com")).isEmpty();

        // Örnek kitap ve teslim noktası da oluşmamalı
        assertThat(books.count()).isZero();
        assertThat(points.count()).isZero();
    }
}
