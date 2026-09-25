package app.kitappla.api;

import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Yöneticinin öğrenci belgesini mobil uygulamada görmesi: {@code GET /api/v1/admin/docs/{userId}/file}, web ucuyla
 * ({@code /admin/belge/{id}}) aynı güvenlik başlıklarıyla dosyayı döndürür; üyeye kapalıdır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBelgeDosyasiApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Value("${kitappla.upload-dir}") String uploadDir;

    private User mk(String tag, boolean admin, String documentPath) {
        User u = new User();
        u.setName("Belge " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("x"));
        u.setAdmin(admin);
        u.setDocumentPath(documentPath);
        return users.save(u);
    }

    private String belgeYaz(byte[] icerik) throws Exception {
        Path dir = Path.of(uploadDir, "documents");
        Files.createDirectories(dir);
        String ad = UUID.randomUUID() + ".pdf";
        Files.write(dir.resolve(ad), icerik);
        return ad;
    }

    @Test
    void yonetici_belgeyi_guvenlik_basliklariyla_alir() throws Exception {
        byte[] pdf = "%PDF-1.4 ogrenci-belgesi".getBytes(StandardCharsets.US_ASCII);
        User ogrenci = mk("belgeli", false, belgeYaz(pdf));
        User yonetici = mk("yonetici", true, null);

        mvc.perform(get("/api/v1/admin/docs/" + ogrenci.getId() + "/file").with(user(new AppUserDetails(yonetici))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"belge-" + ogrenci.getId() + ".pdf\""))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void belgesi_olmayan_uye_404_json() throws Exception {
        User belgesiz = mk("belgesiz", false, null);
        User yonetici = mk("yonetici", true, null);

        mvc.perform(get("/api/v1/admin/docs/" + belgesiz.getId() + "/file").with(user(new AppUserDetails(yonetici))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Belge bulunamadı."));
    }

    @Test
    void uye_belgeye_erisemez() throws Exception {
        User ogrenci = mk("belgeli", false, belgeYaz("%PDF-1.4 x".getBytes(StandardCharsets.US_ASCII)));
        User baskaUye = mk("meraklı", false, null);

        mvc.perform(get("/api/v1/admin/docs/" + ogrenci.getId() + "/file").with(user(new AppUserDetails(baskaUye))))
                .andExpect(status().isForbidden());
    }
}
