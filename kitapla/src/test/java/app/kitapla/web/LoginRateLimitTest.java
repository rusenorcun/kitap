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
        attempts.resetIp("127.0.0.1");
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

    @Test
    void passwordSprayingAyniIpdenYapildigindaIpKilitlenir() throws Exception {
        String testIp = "198.51.100.50";
        attempts.resetIp(testIp);

        // Tek bir IP'den farklı farklı kullanıcılara 1'er kez hatalı şifre denenmesi
        for (int i = 0; i < attempts.maxAttemptsIp(); i++) {
            String sprayedEmail = "spray-" + i + "-" + UUID.randomUUID() + "@test.local";
            mvc.perform(post("/login").with(csrf())
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .param("email", sprayedEmail)
                            .param("password", "ortaksifre"))
                    .andExpect(redirectedUrl(i < attempts.maxAttemptsIp() - 1 ? "/login?error" : "/login?kilit"));
        }

        // IP sınırını aştıktan sonra hiç denenmemiş yeni bir kullanıcı bile bu IP'den engellenir
        String yeniEmail = "yeni-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/login").with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .param("email", yeniEmail)
                        .param("password", "herhangisifre"))
                .andExpect(redirectedUrl("/login?kilit"));

        assertThat(attempts.isIpBlocked(testIp)).isTrue();
        assertThat(attempts.isBlocked(yeniEmail, testIp)).isTrue();
    }

    @Test
    void isBlockedSadeceSorgulamaylaBellekteYeniGirdiOlusturmaz() {
        String rasgeleEmail = "hic-yok-" + UUID.randomUUID() + "@test.local";
        String rasgeleIp = "192.0.2.100";

        assertThat(attempts.isBlocked(rasgeleEmail, rasgeleIp)).isFalse();
        assertThat(attempts.isBlocked(LoginAttemptService.key(rasgeleEmail, rasgeleIp))).isFalse();
    }

    @Test
    void eszamanliHataliGirislerdeVeriYapisiBozulmaz() throws Exception {
        String testEmail = "concurrent-" + UUID.randomUUID() + "@test.local";
        String testIp = "192.0.2.200";

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(10);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < 50; i++) {
            futures.add(executor.submit(() -> attempts.recordFailure(testEmail, testIp)));
        }

        for (java.util.concurrent.Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(attempts.isBlocked(testEmail, testIp)).isTrue();
    }
}
