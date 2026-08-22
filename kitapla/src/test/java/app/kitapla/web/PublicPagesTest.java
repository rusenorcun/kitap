package app.kitapla.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Herkese açık sayfalar ve temel erişim kuralları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPagesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anaSayfaAcilir() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KİTAPLA")));
    }

    @Test
    void girisVeKayitSayfalariAcilir() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("register"));
    }

    @Test
    void sssSayfasiAcilir() throws Exception {
        mvc.perform(get("/sss"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Öğrenci önceliği")));
    }

    @Test
    void korunanSayfalarGirisIster() throws Exception {
        mvc.perform(get("/panom"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminSayfasiNormalKullaniciyaKapali() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
