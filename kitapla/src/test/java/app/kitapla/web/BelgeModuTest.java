package app.kitapla.web;

import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Öğrenci doğrulaması okul e-postasına taşındı; belgeyle başvuru <b>silinmedi</b>, kapatıldı.
 * Bu test bayrak açıldığında eski akışın hâlâ çalıştığını doğrular.
 */
@SpringBootTest(properties = "kitapla.features.document=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BelgeModuTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Value("${kitapla.upload-dir}") String uploadDir;

    private User mk(String tag) {
        User u = new User();
        u.setName("Belge " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        return users.save(u);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(users.findById(u.getId()).orElseThrow());
    }

    private long belgeSayisi() throws Exception {
        Path dir = Path.of(uploadDir, "documents");
        if (!Files.isDirectory(dir)) return 0;
        try (var s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void ogrenciBasvurusuBelgeYuklerVeBeklemeyeAlir() throws Exception {
        User u = mk("basvuru");
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
    void basarisizOgrenciBasvurusuBelgeBirakmaz() throws Exception {
        // Adres artık zorunlu değil; hatayı okul seviyesini boş bırakarak tetikliyoruz
        User u = mk("eksik-seviye");
        var belge = new MockMultipartFile("document", "b.pdf", "application/pdf", "x".getBytes());
        long oncekiBelgeSayisi = belgeSayisi();

        mvc.perform(multipart("/profil/ogrenci").file(belge).with(user(as(u))).with(csrf())
                        .param("documentNo", "LS-Z"))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        // Başvuru reddedildiyse diske yazılan belge geride kalmamalı
        assertThat(belgeSayisi()).isEqualTo(oncekiBelgeSayisi);
    }

    @Test
    void belgeliKayitOgrenciDogrulamasiniBeklemeyeAlir() throws Exception {
        var belge = new MockMultipartFile("document", "belge.pdf", "application/pdf", "sahte-belge".getBytes());
        String eposta = "belgeli-" + UUID.randomUUID() + "@test.local";

        mvc.perform(multipart("/register").file(belge).with(csrf())
                        .param("name", "Test Öğrenci")
                        .param("email", eposta)
                        .param("password", "sifre123")
                        .param("wantsStudent", "true")
                        .param("schoolLevel", "LISE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/login?kayit"));

        User saved = users.findByEmail(eposta).orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(saved.isStudent()).isFalse(); // onaylanana kadar öğrenci sayılmaz
        assertThat(saved.getDocumentPath()).isNotBlank();
    }

    @Test
    void belgeFormuKayitSayfasindaGorunur() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(content().string(containsString("Öğrenci belgesi")));
    }
}
