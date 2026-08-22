package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hatalı giriş denemelerinin sınırlanması. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired LoginAttemptService attempts;

    private String email;

    @BeforeEach
    void setUp() {
        email = "kilit-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setName("Kilit Denek");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("dogrusifre1"));
        users.save(u);
        attempts.reset(LoginAttemptService.key(email, "127.0.0.1"));
    }

    private void dene(String sifre, String beklenenHedef) throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", sifre))
                .andExpect(redirectedUrl(beklenenHedef));
    }

    @Test
    void sinirAsilincaGirisKilitlenir() throws Exception {
        for (int i = 0; i < attempts.maxAttempts() - 1; i++) dene("yanlis", "/login?error");

        dene("yanlis", "/login?kilit");          // sınırı dolduran deneme
        dene("dogrusifre1", "/login?kilit");     // artık doğru şifre bile denenmiyor

        assertThat(attempts.isBlocked(LoginAttemptService.key(email, "127.0.0.1"))).isTrue();
    }

    @Test
    void basariliGirisSayaciSifirlar() throws Exception {
        dene("yanlis", "/login?error");
        dene("yanlis", "/login?error");

        dene("dogrusifre1", "/panom");

        assertThat(attempts.isBlocked(LoginAttemptService.key(email, "127.0.0.1"))).isFalse();
        // Sayaç sıfırlandığı için yeniden tam hakkımız var
        for (int i = 0; i < attempts.maxAttempts() - 1; i++) dene("yanlis", "/login?error");
    }

    @Test
    void kilitMesajiGirisSayfasindaGosterilir() throws Exception {
        mvc.perform(get("/login").param("kilit", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Çok fazla hatalı deneme")))
                .andExpect(content().string(containsString(attempts.windowMinutes() + " dakika")));
    }

    @Test
    void farkliKullanicilarBirbirininSayacindanEtkilenmez() throws Exception {
        for (int i = 0; i < attempts.maxAttempts(); i++) dene("yanlis", i < attempts.maxAttempts() - 1 ? "/login?error" : "/login?kilit");

        String digeri = "temiz-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setName("Temiz Üye");
        u.setEmail(digeri);
        u.setPasswordHash(encoder.encode("dogrusifre1"));
        users.save(u);

        mvc.perform(post("/login").with(csrf()).param("email", digeri).param("password", "dogrusifre1"))
                .andExpect(redirectedUrl("/panom"));
    }
}
