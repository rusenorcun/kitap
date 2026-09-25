package app.kitappla.web;

import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * E-postadaki bağlantıya oturum kapalıyken tıklayan üye girişten sonra o sayfaya döner;
 * arka plan istekleri (HTMX parçası, canlı akış) dönüş hedefi olarak hatırlanmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GirisSonrasiYonlendirmeTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired LoginAttemptService attempts;

    private String email;

    @BeforeEach
    void setUp() {
        email = "donus-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setName("Dönüş Denek");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("dogrusifre1"));
        users.save(u);
        attempts.resetIp("127.0.0.1");
    }

    private String girisYap(MockHttpSession session) throws Exception {
        return mvc.perform(post("/login").session(session).with(csrf())
                        .param("email", email).param("password", "dogrusifre1"))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    void epostaBaglantisiGiristenSonraAcilir() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/profil/ogrenci/eposta/onay?token=abc123").session(session)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("http://localhost/login"));

        assertThat(girisYap(session)).contains("/profil/ogrenci/eposta/onay").contains("token=abc123");
    }

    @Test
    void htmxParcasiDonusHedefiOlmaz() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/mesajlar/1/liste").header("HX-Request", "true").session(session)
                .accept(MediaType.TEXT_HTML));

        assertThat(girisYap(session)).isEqualTo("/panom");
    }

    @Test
    void dogrudanGiristePanoyaGider() throws Exception {
        assertThat(girisYap(new MockHttpSession())).isEqualTo("/panom");
    }
}
