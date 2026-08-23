package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.mail.MailService;
import app.kitapla.repo.UserRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Şifre sıfırlama sayfaları ve uçtan uca akış. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MailService mail;

    @BeforeEach
    void temizle() { mail.clearOutbox(); }

    private User mk(String tag) {
        User u = new User();
        u.setName("Sayfa " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("eskisifre1"));
        return users.save(u);
    }

    private String sonJeton() {
        var son = mail.outbox().get(mail.outbox().size() - 1);
        String s = son.html();
        int i = s.indexOf("/sifre-sifirla?token=");
        return s.substring(i + "/sifre-sifirla?token=".length()).split("[\"'<\\s]")[0];
    }

    @Test
    void sayfalarGirisIstemedenAcilir() throws Exception {
        mvc.perform(get("/sifremi-unuttum"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Şifremi unuttum")));
        mvc.perform(get("/sifre-sifirla").param("token", "yok"))
                .andExpect(status().isOk());
    }

    @Test
    void girisSayfasindaBaglanti() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("/sifremi-unuttum")));
    }

    @Test
    void uctanUcaSifreSifirlanirVeYeniSifreyleGirisYapilir() throws Exception {
        User u = mk("uctanuca");

        mvc.perform(post("/sifremi-unuttum").with(csrf()).param("email", u.getEmail()))
                .andExpect(redirectedUrl("/sifremi-unuttum"))
                .andExpect(flash().attributeExists("basari"));

        String jeton = sonJeton();

        mvc.perform(get("/sifre-sifirla").param("token", jeton))
                .andExpect(content().string(containsString("Yeni şifre belirle")))
                .andExpect(content().string(not(containsString("Bağlantı geçersiz"))));

        mvc.perform(post("/sifre-sifirla").with(csrf())
                        .param("token", jeton)
                        .param("newPassword", "yenisifre1")
                        .param("confirmPassword", "yenisifre1"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("basari"));

        // Yeni şifre gerçekten geçerli, eskisi değil
        mvc.perform(post("/login").with(csrf())
                        .param("email", u.getEmail()).param("password", "yenisifre1"))
                .andExpect(redirectedUrl("/panom"));
        mvc.perform(post("/login").with(csrf())
                        .param("email", u.getEmail()).param("password", "eskisifre1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void gecersizJetonlaFormGosterilmez() throws Exception {
        mvc.perform(get("/sifre-sifirla").param("token", "uydurma"))
                .andExpect(content().string(containsString("Bağlantı geçersiz")))
                .andExpect(content().string(not(containsString("name=\"newPassword\""))));
    }

    @Test
    void kayitliOlmayanAdresIcinDeAyniCevapDoner() throws Exception {
        // Cevap, adresin kayıtlı olup olmadığını sızdırmamalı
        User kayitli = mk("sizdirma");

        var a = mvc.perform(post("/sifremi-unuttum").with(csrf()).param("email", kayitli.getEmail()))
                .andReturn().getFlashMap().get("basari");
        var b = mvc.perform(post("/sifremi-unuttum").with(csrf())
                        .param("email", "yok-" + UUID.randomUUID() + "@test.local"))
                .andReturn().getFlashMap().get("basari");

        assertThat(a).isEqualTo(b);
    }
}
