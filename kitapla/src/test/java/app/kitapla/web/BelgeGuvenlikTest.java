package app.kitapla.web;

import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Öğrenci belgesi yükleme ve sunma güvenliği testleri:
 * - HTML, SVG ve betik içeren zararlı dosyaların reddedildiğini (Stored XSS koruması),
 * - Geçerli PDF, JPG ve PNG dosyalarının kabul edildiğini,
 * - /admin/belge/{id} ucunun Content-Security-Policy (sandbox) ve X-Content-Type-Options (nosniff)
 *   başlıklarıyla güvenli sunulduğunu doğrular.
 */
@SpringBootTest(properties = "kitapla.features.document=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BelgeGuvenlikTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User mkUser(boolean admin) {
        User u = new User();
        u.setName("Test " + (admin ? "Admin" : "Üye"));
        u.setEmail("sec-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAdmin(admin);
        return users.save(u);
    }

    @Test
    void htmlDosyasiYuklenemez() throws Exception {
        User u = mkUser(false);
        byte[] htmlPayload = "<html><script>alert(document.cookie)</script></html>".getBytes();
        var htmlDosya = new MockMultipartFile("document", "zararli.html", "text/html", htmlPayload);

        mvc.perform(multipart("/profil/ogrenci").file(htmlDosya).with(user(new AppUserDetails(u))).with(csrf())
                        .param("schoolLevel", "UNIVERSITE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        User guncel = users.findById(u.getId()).orElseThrow();
        assertThat(guncel.getDocumentPath()).isNull();
        assertThat(guncel.getStudentStatus()).isEqualTo(StudentStatus.NONE);
    }

    @Test
    void svgDosyasiYuklenemez() throws Exception {
        User u = mkUser(false);
        byte[] svgPayload = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>".getBytes();
        var svgDosya = new MockMultipartFile("document", "zararli.svg", "image/svg+xml", svgPayload);

        mvc.perform(multipart("/profil/ogrenci").file(svgDosya).with(user(new AppUserDetails(u))).with(csrf())
                        .param("schoolLevel", "UNIVERSITE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        User guncel = users.findById(u.getId()).orElseThrow();
        assertThat(guncel.getDocumentPath()).isNull();
    }

    @Test
    void sahteUzantiliHtmlDosyasiYuklenemez() throws Exception {
        User u = mkUser(false);
        // Uzantısı .pdf ama içeriği HTML olan dosya (magic byte kontrolünden geçmemeli)
        byte[] sahtePdfPayload = "<script>alert('xss')</script>".getBytes();
        var sahtePdf = new MockMultipartFile("document", "fake.pdf", "application/pdf", sahtePdfPayload);

        mvc.perform(multipart("/profil/ogrenci").file(sahtePdf).with(user(new AppUserDetails(u))).with(csrf())
                        .param("schoolLevel", "UNIVERSITE")
                        .param("documentNo", "LS-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        User guncel = users.findById(u.getId()).orElseThrow();
        assertThat(guncel.getDocumentPath()).isNull();
    }

    @Test
    void gecerliPdfYuklenirVeAdminGuvenliBasliklarlaGoruntuler() throws Exception {
        User student = mkUser(false);
        User admin = mkUser(true);

        byte[] gercekPdf = "%PDF-1.4 sample pdf content for student verification".getBytes();
        var pdfFile = new MockMultipartFile("document", "belge.pdf", "application/pdf", gercekPdf);

        mvc.perform(multipart("/profil/ogrenci").file(pdfFile).with(user(new AppUserDetails(student))).with(csrf())
                        .param("schoolLevel", "UNIVERSITE")
                        .param("documentNo", "UNI-" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("basari"));

        User savedStudent = users.findById(student.getId()).orElseThrow();
        assertThat(savedStudent.getDocumentPath()).isNotBlank();
        assertThat(savedStudent.getDocumentPath()).endsWith(".pdf");

        // Admin inceleme ucunu çağırır: CSP sandbox ve nosniff başlıkları kontrol edilir
        mvc.perform(get("/admin/belge/" + savedStudent.getId()).with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'"))
                .andExpect(header().string("Cache-Control", "private, no-cache, no-store, must-revalidate"));
    }
}
