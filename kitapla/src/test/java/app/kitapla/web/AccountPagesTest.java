package app.kitapla.web;

import app.kitapla.domain.School;
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
                        .param("school", "ATATURK_UNIVERSITESI")
                        .param("phone", "05551112233"))
                .andExpect(redirectedUrl("/profil"));

        User kayitli = users.findById(u.getId()).orElseThrow();
        assertThat(kayitli.getName()).isEqualTo("Güncel Ad");
        assertThat(kayitli.getSchool()).isEqualTo(School.ATATURK_UNIVERSITESI);
        // Adres alanı kampüs modunda formda yok; gelmeyen değer kayıtlı adresi silmemeli
        assertThat(kayitli.getAddress()).isEqualTo("Eski");

        // Oturumdaki (bayat) kullanıcıyla istesek bile sayfa güncel veriyi göstermeli
        mvc.perform(get("/profil").with(user(as(u))))
                .andExpect(content().string(containsString("Güncel Ad")))
                .andExpect(content().string(containsString("Atatürk Üniversitesi")))
                // Teslim yüz yüze: adres hiç sorulmuyor
                .andExpect(content().string(not(containsString("Teslimat adresi"))));
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
    void okulEpostasiyleOgrenciOlunur() throws Exception {
        User u = mk("edu", "İzmir");

        mvc.perform(post("/profil/ogrenci/eposta").with(user(as(u))).with(csrf())
                        .param("studentEmail", "ogr-" + UUID.randomUUID() + "@atauni.edu.tr"))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(users.findById(u.getId()).orElseThrow().getStudentStatus())
                .isEqualTo(StudentStatus.APPROVED);
    }

    @Test
    void eduTrOlmayanAdresReddedilir() throws Exception {
        User u = mk("edu-degil", "İzmir");

        mvc.perform(post("/profil/ogrenci/eposta").with(user(as(u))).with(csrf())
                        .param("studentEmail", "birisi@gmail.com"))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        assertThat(users.findById(u.getId()).orElseThrow().getStudentStatus())
                .isEqualTo(StudentStatus.NONE);
    }

    @Test
    void belgeliBasvuruKapaliykenDosyaYazilmaz() throws Exception {
        User u = mk("belge-kapali", "İzmir");
        var belge = new MockMultipartFile("document", "belge.pdf", "application/pdf", "sahte".getBytes());
        long oncekiBelgeSayisi = belgeSayisi();

        mvc.perform(multipart("/profil/ogrenci").file(belge).with(user(as(u))).with(csrf())
                        .param("schoolLevel", "LISE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        assertThat(users.findById(u.getId()).orElseThrow().getStudentStatus())
                .isEqualTo(StudentStatus.NONE);
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

    @Test
    void panomdaTakasBaglantilariGorunur() throws Exception {
        User u = mk("pano-takas", "İzmir");

        mvc.perform(get("/panom").with(user(as(u))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/takas/takaslarim")))
                .andExpect(content().string(containsString("Takaslarım")))
                .andExpect(content().string(containsString("/takas/kitaplarim")))
                .andExpect(content().string(containsString("Takas Kitaplarım")));
    }
}
