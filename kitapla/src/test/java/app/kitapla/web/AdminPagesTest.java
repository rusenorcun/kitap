package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Yönetim panelinin erişim denetimi ve sayfaları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired PasswordEncoder encoder;
    @Value("${kitapla.upload-dir}") String uploadDir;

    private User mk(String tag, boolean isAdmin) {
        User u = new User();
        u.setName("Panel " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        u.setAdmin(isAdmin);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    /**
     * Giriş anındaki kullanıcının kopyası. Aynı nesneyi paylaşmadığı için sonraki
     * veritabanı değişikliklerini görmez; "bayat oturum" durumunu gerçekten kurar.
     */
    private AppUserDetails oturumKopyasi(User u) {
        User snapshot = new User();
        snapshot.setId(u.getId());
        snapshot.setName(u.getName());
        snapshot.setEmail(u.getEmail());
        snapshot.setPasswordHash(u.getPasswordHash());
        snapshot.setAddress(u.getAddress());
        snapshot.setAdmin(u.isAdmin());
        snapshot.setBlocked(u.isBlocked());
        snapshot.setStudentStatus(u.getStudentStatus());
        snapshot.setSchoolLevel(u.getSchoolLevel());
        return new AppUserDetails(snapshot);
    }

    private User mkPending(String tag, String content) throws Exception {
        User u = mk(tag, false);
        Path dir = Path.of(uploadDir, "documents");
        Files.createDirectories(dir);
        String fileName = "panel-" + UUID.randomUUID() + ".txt";
        Files.writeString(dir.resolve(fileName), content);
        u.setStudentStatus(StudentStatus.PENDING);
        u.setSchoolLevel(SchoolLevel.LISE);
        u.setDocumentNo("PNL-" + UUID.randomUUID());
        u.setDocumentPath(fileName);
        return users.save(u);
    }

    // ---------- Erişim denetimi ----------

    @Test
    void yonetimGirisIster() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void siradanUyeYonetimePasGecemez() throws Exception {
        User u = mk("siradan", false);
        mvc.perform(get("/admin").with(user(as(u)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/uyeler").with(user(as(u)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/icerik").with(user(as(u)))).andExpect(status().isForbidden());
    }

    @Test
    void siradanUyeYonetimIslemiYapamaz() throws Exception {
        User u = mk("saldirgan", false);
        User hedef = mk("hedef", false);

        mvc.perform(post("/admin/uyeler/" + hedef.getId() + "/askiya-al").with(user(as(u))).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(users.findById(hedef.getId()).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void siradanUyeBaskasininBelgesiniGoremez() throws Exception {
        User pending = mkPending("gizli", "gizli belge icerigi");
        User u = mk("meraklı", false);

        mvc.perform(get("/admin/belge/" + pending.getId()).with(user(as(u))))
                .andExpect(status().isForbidden());
    }

    // ---------- Pano ----------

    @Test
    void panoSayaclariGosterir() throws Exception {
        User yonetici = mk("pano", true);
        mvc.perform(get("/admin").with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Yönetim panosu")))
                .andExpect(content().string(containsString("Onaylı öğrenci")))
                .andExpect(content().string(containsString("Bekleyen belge")));
    }

    @Test
    void yonetimBaglantisiSadeceYoneticiyeGorunur() throws Exception {
        User yonetici = mk("navadmin", true);
        User uye = mk("navuye", false);

        mvc.perform(get("/panom").with(user(as(yonetici))))
                .andExpect(content().string(containsString("Yönetim")));
        mvc.perform(get("/panom").with(user(as(uye))))
                .andExpect(content().string(not(containsString(">Yönetim<"))));
    }

    // ---------- Belgeler ----------

    @Test
    void bekleyenBelgeListelenirVeOnaylanir() throws Exception {
        User yonetici = mk("belgeci", true);
        User pending = mkPending("bekleyen", "belge");

        mvc.perform(get("/admin/belgeler").with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(pending.getName())))
                .andExpect(content().string(containsString(pending.getDocumentNo())));

        mvc.perform(post("/admin/belgeler/" + pending.getId() + "/onayla")
                        .with(user(as(yonetici))).with(csrf()))
                .andExpect(redirectedUrl("/admin/belgeler"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(users.findById(pending.getId()).orElseThrow().getStudentStatus())
                .isEqualTo(StudentStatus.APPROVED);
    }

    @Test
    void belgeYalnizcaYoneticiyeServisEdilir() throws Exception {
        User yonetici = mk("belgeokur", true);
        User pending = mkPending("okunacak", "sahte belge icerigi");

        mvc.perform(get("/admin/belge/" + pending.getId()).with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sahte belge icerigi")));
    }

    @Test
    void belgeReddedilirVeGerekceBildirilir() throws Exception {
        User yonetici = mk("retci", true);
        User pending = mkPending("reddedilecek", "belge");

        mvc.perform(post("/admin/belgeler/" + pending.getId() + "/reddet")
                        .with(user(as(yonetici))).with(csrf())
                        .param("reason", "Belge süresi geçmiş."))
                .andExpect(redirectedUrl("/admin/belgeler"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(users.findById(pending.getId()).orElseThrow().getStudentStatus())
                .isEqualTo(StudentStatus.REJECTED);
    }

    // ---------- Üyeler ----------

    @Test
    void uyeAramasiFiltreler() throws Exception {
        User yonetici = mk("arayan", true);
        User hedef = mk("bulunacak", false);

        mvc.perform(get("/admin/uyeler").param("q", hedef.getEmail()).with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(hedef.getName())))
                .andExpect(content().string(not(containsString(yonetici.getEmail()))));
    }

    @Test
    void askiyaAlmaVeGeriAlmaCalisir() throws Exception {
        User yonetici = mk("askici", true);
        User hedef = mk("askiya", false);

        mvc.perform(post("/admin/uyeler/" + hedef.getId() + "/askiya-al")
                        .with(user(as(yonetici))).with(csrf()))
                .andExpect(redirectedUrl("/admin/uyeler"))
                .andExpect(flash().attributeExists("basari"));
        assertThat(users.findById(hedef.getId()).orElseThrow().isBlocked()).isTrue();

        mvc.perform(post("/admin/uyeler/" + hedef.getId() + "/aktif-et")
                        .with(user(as(yonetici))).with(csrf()))
                .andExpect(flash().attributeExists("basari"));
        assertThat(users.findById(hedef.getId()).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void kendiniAskiyaAlmaDenemesiHataVerir() throws Exception {
        User yonetici = mk("kendini", true);

        mvc.perform(post("/admin/uyeler/" + yonetici.getId() + "/askiya-al")
                        .with(user(as(yonetici))).with(csrf()))
                .andExpect(redirectedUrl("/admin/uyeler"))
                .andExpect(flash().attributeExists("hata"));
        assertThat(users.findById(yonetici.getId()).orElseThrow().isBlocked()).isFalse();
    }

    // ---------- Oturum tazeleme ----------

    @Test
    void yetkiVerilenUyeYenidenGirisYapmadanPaneleGirer() throws Exception {
        User u = mk("terfi", false);
        AppUserDetails bayatOturum = oturumKopyasi(u);   // giriş anındaki (yetkisiz) principal

        mvc.perform(get("/admin").with(user(bayatOturum))).andExpect(status().isForbidden());

        u.setAdmin(true);
        users.save(u);

        // Aynı bayat oturumla gelse bile yetki güncel satırdan okunmalı
        mvc.perform(get("/admin").with(user(bayatOturum))).andExpect(status().isOk());
    }

    @Test
    void askiyaAlinanUyeninAcikOturumuDuser() throws Exception {
        User u = mk("askili-oturum", false);
        AppUserDetails bayatOturum = oturumKopyasi(u);

        mvc.perform(get("/panom").with(user(bayatOturum))).andExpect(status().isOk());

        u.setBlocked(true);
        users.save(u);

        mvc.perform(get("/panom").with(user(bayatOturum)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void onaylanmisOgrenciYenidenGirisYapmadanOncelikliBagisiAlabilir() throws Exception {
        User donor = mk("oncelik-bagisci", false);
        User u = mk("terfi-ogrenci", false);
        AppUserDetails bayatOturum = oturumKopyasi(u);   // henüz öğrenci değilken alınan oturum

        Book b = new Book();
        b.setTitle("Öncelikli Kitap " + UUID.randomUUID());
        books.save(b);
        Donation d = new Donation();          // yeni bağış: 48 saatlik öğrenci önceliği açık
        d.setDonor(donor);
        d.setBook(b);
        d.setQuantity(1);
        donations.save(d);

        // Üye olarak öncelik penceresinde alamaz
        mvc.perform(post("/kitap/" + d.getId() + "/al").with(user(bayatOturum)).with(csrf()))
                .andExpect(flash().attributeExists("hata"));

        u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(SchoolLevel.LISE);
        users.save(u);

        // Onaydan sonra aynı bayat oturumla, yeniden giriş yapmadan alabilmeli
        mvc.perform(post("/kitap/" + d.getId() + "/al").with(user(bayatOturum)).with(csrf()))
                .andExpect(flash().attributeExists("basari"));
    }

    // ---------- İçerik ----------

    @Test
    void acikBagisListelenirVeKaldirilir() throws Exception {
        User yonetici = mk("moderator", true);
        User donor = mk("bagisci", false);

        Book b = new Book();
        b.setTitle("Moderasyon Kitabı " + UUID.randomUUID());
        books.save(b);
        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(b);
        d.setQuantity(1);
        donations.save(d);

        mvc.perform(get("/admin/icerik").with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(b.getTitle())));

        mvc.perform(post("/admin/icerik/bagis/" + d.getId() + "/kaldir")
                        .with(user(as(yonetici))).with(csrf())
                        .param("reason", "Test gerekçesi"))
                .andExpect(redirectedUrl("/admin/icerik"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(donations.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(DonationStatus.CLOSED);
    }
}
