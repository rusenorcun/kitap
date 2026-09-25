package app.kitappla.web;

import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Yönetim ayrı alan adında yayınlanır: www'deki /admin istekleri yönetim alan adına gider,
 * yönetim alan adı yalnızca yönetim sayfalarını (ve girişini) sunar, geri kalanı www'ye yollar.
 */
@SpringBootTest(properties = {
        "kitappla.base-url=https://www.kitappla.com",
        "kitappla.admin-url=https://admin.kitappla.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class YonetimAlanAdiTest {

    private static final String WWW = "https://www.kitappla.com";
    private static final String YONETIM = "https://admin.kitappla.com";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean admin) {
        User u = new User();
        u.setName("Alan " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAdmin(admin);
        return users.save(u);
    }

    @Test
    void wwwUzerindekiYonetimIstegiYonetimAlanAdinaGider() throws Exception {
        mvc.perform(get(WWW + "/admin/uyeler?q=ali"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(YONETIM + "/admin/uyeler?q=ali"));
        // Yönetim formu www'ye gönderilirse işlenmez
        mvc.perform(post(WWW + "/admin/uyeler/1/askiya-al").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void yonetimAlanAdiKokuYonetimPanosunaGider() throws Exception {
        mvc.perform(get(YONETIM + "/")).andExpect(redirectedUrl("/admin"));
        mvc.perform(get(YONETIM + "/?cikis")).andExpect(redirectedUrl("/login?cikis"));
    }

    @Test
    void yonetimAlanAdindaUyeSayfalariWwwyeYonlenir() throws Exception {
        mvc.perform(get(YONETIM + "/kesfet?q=x"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(WWW + "/kesfet?q=x"));
        mvc.perform(post(YONETIM + "/api/v1/auth/login").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void yonetimAlanAdindaGirisSayfasiVeStatiklerAcik() throws Exception {
        mvc.perform(get(YONETIM + "/login")).andExpect(status().isOk());
        mvc.perform(get(YONETIM + "/css/kitappla.css")).andExpect(status().isOk());
        mvc.perform(get(YONETIM + "/admin")).andExpect(status().isFound())
                .andExpect(redirectedUrl(YONETIM + "/login"));
    }

    @Test
    void yonetimAlanAdindaGirisYonetimPanosunaDoner() throws Exception {
        User admin = mk("giris-yonetici", true);
        mvc.perform(post(YONETIM + "/login").with(csrf())
                        .param("email", admin.getEmail()).param("password", "sifre123"))
                .andExpect(redirectedUrl("/admin"));
        User uye = mk("giris-uye", false);
        mvc.perform(post(WWW + "/login").with(csrf())
                        .param("email", uye.getEmail()).param("password", "sifre123"))
                .andExpect(redirectedUrl("/panom"));
    }

    @Test
    void yonetimMenusuUyeBaglantilariYerineSiteBaglantisiGosterir() throws Exception {
        User admin = mk("menu-yonetici", true);
        mvc.perform(get(YONETIM + "/admin").with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"" + WWW + "\"")))
                .andExpect(content().string(not(containsString("href=\"/bagis/yeni\""))));
        // www'de yöneticinin "Yönetim" bağlantısı doğrudan yönetim alan adını gösterir
        mvc.perform(get(WWW + "/panom").with(user(new AppUserDetails(admin))))
                .andExpect(content().string(containsString("href=\"" + YONETIM + "/admin\"")));
    }
}
