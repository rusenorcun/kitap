package app.kitapla.web;

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
    void belgeliKayitOgrenciDogrulamasiniBeklemeyeAlir() throws Exception {
        var belge = new MockMultipartFile("document", "belge.pdf", "application/pdf", "sahte-belge".getBytes());

        mvc.perform(multipart("/register").file(belge).with(csrf())
                        .param("name", "Test Öğrenci")
                        .param("email", "ogrenci@test.local")
                        .param("password", "sifre123")
                        .param("address", "İzmir")
                        .param("wantsStudent", "true")
                        .param("schoolLevel", "LISE")
                        .param("documentNo", "LS-9001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?kayit"));

        var saved = users.findByEmail("ogrenci@test.local").orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(saved.isStudent()).isFalse(); // onaylanana kadar öğrenci sayılmaz
        assertThat(saved.getDocumentPath()).isNotBlank();
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
