package app.kitapla.web;

import app.kitapla.domain.School;
import app.kitapla.domain.StudentStatus;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Kayıt ve giriş akışı. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void uyeKaydiOlusurVeGirisYapabilir() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Test Üye")
                        .param("email", "uye@test.local")
                        .param("password", "sifre123")
                        .param("address", "İzmir Bornova"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?kayit"));

        var saved = users.findByEmail("uye@test.local").orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.NONE);
        assertThat(saved.isStudent()).isFalse();
        assertThat(saved.getPasswordHash()).isNotEqualTo("sifre123"); // hash'lenmiş

        mvc.perform(formLogin("/login").user("email", "uye@test.local").password("password", "sifre123"))
                .andExpect(authenticated());
    }

    @Test
    void okulEpostasiyleKayitDogrudanOgrenciYapar() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Okullu Üye")
                        .param("email", "ogr@atauni.edu.tr")
                        .param("password", "sifre123")
                        .param("school", "ATATURK_UNIVERSITESI"))
                .andExpect(redirectedUrl("/login?kayit"));

        var saved = users.findByEmail("ogr@atauni.edu.tr").orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.APPROVED);
        assertThat(saved.isStudent()).isTrue();
        assertThat(saved.getStudentEmail()).isEqualTo("ogr@atauni.edu.tr");
        assertThat(saved.getSchool()).isEqualTo(School.ATATURK_UNIVERSITESI);
        // Belge istenmedi
        assertThat(saved.getDocumentPath()).isNull();
    }

    @Test
    void kisiselEpostaylaKayitUyeOlarakBaslar() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Kişisel Üye")
                        .param("email", "kisisel@gmail.com")
                        .param("password", "sifre123")
                        .param("school", "ERZURUM_TEKNIK_UNIVERSITESI"))
                .andExpect(redirectedUrl("/login?kayit"));

        var saved = users.findByEmail("kisisel@gmail.com").orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.NONE);
        assertThat(saved.getSchool()).isEqualTo(School.ERZURUM_TEKNIK_UNIVERSITESI);
    }

    @Test
    void belgeliKayitBayrakKapaliykenYokSayilir() throws Exception {
        var belge = new MockMultipartFile("document", "belge.pdf", "application/pdf", "sahte-belge".getBytes());

        mvc.perform(multipart("/register").file(belge).with(csrf())
                        .param("name", "Belgeli Aday")
                        .param("email", "belgeli@test.local")
                        .param("password", "sifre123")
                        .param("wantsStudent", "true")
                        .param("schoolLevel", "LISE")
                        .param("documentNo", "LS-9001"))
                .andExpect(redirectedUrl("/login?kayit"));

        var saved = users.findByEmail("belgeli@test.local").orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.NONE);
        assertThat(saved.getDocumentPath()).isNull();
    }

    @Test
    void ayniEpostaIkinciKezKullanilamaz() throws Exception {
        mvc.perform(post("/register").with(csrf())
                .param("name", "İlk").param("email", "tekrar@test.local")
                .param("password", "sifre123").param("address", "Adres"));

        mvc.perform(post("/register").with(csrf())
                        .param("name", "İkinci").param("email", "tekrar@test.local")
                        .param("password", "sifre123").param("address", "Adres"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void gecersizEpostaReddedilir() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "X").param("email", "gecersiz")
                        .param("password", "sifre123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void kisaSifreReddedilir() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "X").param("email", "kisa@test.local")
                        .param("password", "123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
    }
}
