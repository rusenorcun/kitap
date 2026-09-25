package app.kitappla.config;

import app.kitappla.repo.BookRepository;
import app.kitappla.repo.PickupPointRepository;
import app.kitappla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * kitappla.seed.demo=false iken yalnızca yönetici hesabı oluşur.
 * Herkese açık kurulumda örnek hesapların (bilinen şifreli) açılmaması için.
 */
@SpringBootTest(properties = "kitappla.seed.demo=false")
@ActiveProfiles("test")
class SeedDemoKapaliTest {

    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PickupPointRepository points;

    @Value("${kitappla.admin.email}") String adminEmail;
    @Value("${kitappla.admin.password}") String adminPassword;
    @Autowired DataSeeder seeder;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired app.kitappla.service.UserService userService;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void degistirilenYoneticiSifresiYenidenBaslatmadaKorunur() {
        var admin = users.findByEmail(adminEmail).orElseThrow();
        userService.changePassword(admin, adminPassword, "YeniTestParolasi123", "YeniTestParolasi123");
        String changedHash = users.findByEmail(adminEmail).orElseThrow().getPasswordHash();

        seeder.run();
        seeder.run();

        var reloaded = users.findByEmail(adminEmail).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(changedHash);
        assertThat(encoder.matches("YeniTestParolasi123", reloaded.getPasswordHash())).isTrue();
        assertThat(encoder.matches(adminPassword, reloaded.getPasswordHash())).isFalse();
    }

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
