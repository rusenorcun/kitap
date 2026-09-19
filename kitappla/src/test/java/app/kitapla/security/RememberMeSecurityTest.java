package app.kitapla.security;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberMeSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserSessionService userSessionService;

    private String email;
    private User testUser;

    @BeforeEach
    void setUp() {
        email = "remember-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setName("Beni Hatırla Test");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("gucluparola123"));
        testUser = users.save(u);
    }

    @Test
    void beniHatirlaIsaretliyseKullaniciyaGuvenliCerezVerilirVeDbYeKaydedilir() throws Exception {
        MvcResult loginResult = mvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "gucluparola123")
                        .param("remember-me", "on"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panom"))
                .andExpect(cookie().exists(KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME))
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie(KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).as("Remember-me çerezi HttpOnly olmalı").isTrue();
        assertThat(cookie.getMaxAge()).as("Kalıcı çerez süresi pozitif olmalı").isGreaterThan(0);
        assertThat(cookie.getAttribute("SameSite")).as("SameSite Lax olmalı").isEqualToIgnoringCase("Lax");

        // Veritabanında persistent_logins kaydı oluşmalı
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                email
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void kaliciCerezIleOturumsuzKorumaliSayfayaErisilebilir() throws Exception {
        // 1. Beni hatırla ile giriş yap
        MvcResult loginResult = mvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "gucluparola123")
                        .param("remember-me", "on"))
                .andReturn();

        Cookie rememberCookie = loginResult.getResponse().getCookie(KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME);
        assertThat(rememberCookie).isNotNull();

        // 2. Oturum çerezi (JSESSIONID/KITAPLA_SESSION) olmadan SADECE remember-me çereziyle korumalı sayfaya istek at
        mvc.perform(get("/panom").cookie(rememberCookie))
                .andExpect(status().isOk());
    }

    @Test
    void cikisYapildigindaKaliciCerezVeDbKaydiSilinir() throws Exception {
        MvcResult loginResult = mvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "gucluparola123")
                        .param("remember-me", "on"))
                .andReturn();

        Cookie rememberCookie = loginResult.getResponse().getCookie(KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME);
        assertThat(rememberCookie).isNotNull();

        // Çıkış yap
        mvc.perform(post("/logout").with(csrf()).cookie(rememberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().maxAge(KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME, 0));

        // Veritabanındaki satır silinmiş olmalı
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                email
        );
        assertThat(count).isZero();
    }

    @Test
    void sifreDegistigindeVeyaOturumlarDusuruldugundeDbdenSilinir() throws Exception {
        mvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "gucluparola123")
                        .param("remember-me", "on"))
                .andReturn();

        Integer countBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                email
        );
        assertThat(countBefore).isEqualTo(1);

        // Kullanıcının oturumları geçersiz kılındığında
        userSessionService.expireUserSessions(testUser.getId());

        Integer countAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                email
        );
        assertThat(countAfter).isZero();
    }
}
