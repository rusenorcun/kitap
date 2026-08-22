package app.kitapla.web;

import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Profil, öğrenci başvurusu ve bildirim sayfaları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired NotificationService notifications;
    @Autowired PasswordEncoder encoder;
    @Value("${kitapla.upload-dir}") String uploadDir;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Hesap " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    @Test
    void profilSayfasiGirisIster() throws Exception {
        mvc.perform(get("/profil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void profilSayfasiKotaVeDurumGosterir() throws Exception {
        User u = mk("goruntule", "İzmir");
        mvc.perform(get("/profil").with(user(as(u))))
                .andExpect(status().isOk())
                .andExpect(view().name("profil"))
                .andExpect(content().string(containsString("Hesap ayarları")))
                .andExpect(content().string(containsString("Öğrenci misin?")));
    }

    @Test
    void profilGuncellenirVeSayfadaGorunur() throws Exception {
        User u = mk("form", "Eski");
        mvc.perform(post("/profil").with(user(as(u))).with(csrf())
                        .param("name", "Güncel Ad")
                        .param("address", "Yeni Adres 7")
                        .param("phone", "05551112233"))
                .andExpect(redirectedUrl("/profil"));

        assertThat(users.findById(u.getId()).orElseThrow().getName()).isEqualTo("Güncel Ad");
        // Oturumdaki (bayat) kullanıcıyla istesek bile sayfa güncel veriyi göstermeli
        mvc.perform(get("/profil").with(user(as(u))))
                .andExpect(content().string(containsString("Yeni Adres 7")))
                .andExpect(content().string(containsString("Güncel Ad")));
    }

    @Test
    void sifreDegistirmeHataliMevcutSifredeUyarir() throws Exception {
        User u = mk("sifre", "İzmir");
        mvc.perform(post("/profil/sifre").with(user(as(u))).with(csrf())
                        .param("currentPassword", "yanlis")
                        .param("newPassword", "yenisifre1")
                        .param("confirmPassword", "yenisifre1"))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("hata"));
    }

    @Test
    void sifreBasariylaDegisir() throws Exception {
        User u = mk("sifre2", "İzmir");
        mvc.perform(post("/profil/sifre").with(user(as(u))).with(csrf())
                        .param("currentPassword", "sifre123")
                        .param("newPassword", "yenisifre1")
                        .param("confirmPassword", "yenisifre1"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(encoder.matches("yenisifre1",
                users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void ogrenciBasvurusuBelgeYuklerVeBeklemeyeAlir() throws Exception {
        User u = mk("ogrenci", "İzmir Bornova");
        var belge = new MockMultipartFile("document", "belge.pdf", "application/pdf", "sahte".getBytes());

        mvc.perform(multipart("/profil/ogrenci").file(belge).with(user(as(u))).with(csrf())
                        .param("schoolLevel", "LISE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("basari"));

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(saved.getDocumentPath()).isNotBlank();

        mvc.perform(get("/profil").with(user(as(saved))))
                .andExpect(content().string(containsString("incelemede")));
    }

    @Test
    void adressizOgrenciBasvurusuHataVerir() throws Exception {
        User u = mk("adressiz", null);
        var belge = new MockMultipartFile("document", "b.pdf", "application/pdf", "x".getBytes());
        long oncekiBelgeSayisi = belgeSayisi();

        mvc.perform(multipart("/profil/ogrenci").file(belge).with(user(as(u))).with(csrf())
                        .param("schoolLevel", "LISE").param("documentNo", "LS-Z"))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        // Başvuru reddedildiyse diske yazılan belge geride kalmamalı
        assertThat(belgeSayisi()).isEqualTo(oncekiBelgeSayisi);
    }

    private long belgeSayisi() throws Exception {
        Path dir = Path.of(uploadDir, "documents");
        if (!Files.isDirectory(dir)) return 0;
        try (var s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void bildirimlerListelenirVeOkunduIsaretlenir() throws Exception {
        User u = mk("bildirim", "İzmir");
        notifications.notify(u, "test", "İlk bildirimim");

        mvc.perform(get("/bildirimler").with(user(as(u))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("İlk bildirimim")))
                .andExpect(content().string(containsString("Okundu")));

        var n = notifications.latest(u).get(0);
        mvc.perform(post("/bildirimler/" + n.getId() + "/okundu").with(user(as(u))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))   // HTMX parçası
                .andExpect(content().string(containsString("okundu")));

        assertThat(notifications.unreadCount(u)).isZero();
    }

    @Test
    void tumunuOkunduIsaretle() throws Exception {
        User u = mk("hepsi", "İzmir");
        notifications.notify(u, "test", "A");
        notifications.notify(u, "test", "B");

        mvc.perform(post("/bildirimler/hepsi-okundu").with(user(as(u))).with(csrf()))
                .andExpect(redirectedUrl("/bildirimler"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(notifications.unreadCount(u)).isZero();
    }

    @Test
    void navdaOkunmamisRozetiGorunur() throws Exception {
        User u = mk("rozet", "İzmir");
        notifications.notify(u, "test", "Rozet testi");

        mvc.perform(get("/panom").with(user(as(u))))
                .andExpect(content().string(containsString("/bildirimler")));
    }
}
