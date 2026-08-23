package app.kitapla.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Herkese açık sayfalar ve temel erişim kuralları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPagesTest {

    @Autowired
    MockMvc mvc;

    @org.springframework.beans.factory.annotation.Value("${kitapla.contact.email}")
    String contactEmail;

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
    void bilgiSayfalariGirisIstemedenAcilir() throws Exception {
        mvc.perform(get("/kurallar"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("48 saat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Moderasyon")));

        mvc.perform(get("/gizlilik"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BCrypt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("yalnızca yönetici")));

        mvc.perform(get("/iletisim"))
                .andExpect(status().isOk())
                // İletişim adresi yapılandırmadan gelir, şablona gömülmez
                .andExpect(content().string(org.hamcrest.Matchers.containsString(contactEmail)));
    }

    @Test
    void altbilgiBaglantilariGercekSayfalaraGider() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        // Bu bağlantılar bir dönem tıklanamayan <span>'lardı
        assertThat(html).contains("href=\"/kurallar\"")
                        .contains("href=\"/gizlilik\"")
                        .contains("href=\"/iletisim\"");
    }

    @Test
    void saglikUcuGirisIstemedenCalisirVeBilgiSizdirmaz() throws Exception {
        // Docker HEALTHCHECK bu ucu kullanır; kimlik doğrulaması istememeli
        String govde = mvc.perform(get("/saglik"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(govde).isEqualTo("iyi");
        // Sürüm, yapılandırma ya da bağımlılık ayrıntısı sızmamalı
        assertThat(govde.toLowerCase()).doesNotContain("spring", "version", "h2", "jdbc");
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
