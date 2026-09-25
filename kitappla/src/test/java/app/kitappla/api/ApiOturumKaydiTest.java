package app.kitappla.api;

import app.kitappla.domain.TokenType;
import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.security.LoginAttemptService;
import app.kitappla.service.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobil uygulamanın API ile açtığı oturumlar web oturumları gibi SessionRegistry'ye kaydedilmeli; aksi hâlde yönetim
 * izleci onları göstermez ve yönetici sonlandıramaz. Sonlandırılan API oturumu yönlendirme ya da düz metin değil
 * 401 + {@code SESSION_EXPIRED} almalı (uygulama 401'de çıkış yapar).
 * <p>
 * Şifre değişince/sıfırlanınca oturumun düşmesini {@code FreshPrincipalFilter} zaten sağlıyor; o senaryolar burada
 * API oturumları için gerileme testi olarak durur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiOturumKaydiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokens;
    @Autowired SessionRegistry sessionRegistry;
    @Autowired LoginAttemptService loginAttempts;
    @Autowired app.kitappla.security.SessionTokenService sessionTokens;

    private static final String SIFRE = "eskiSifre123";
    private User uye;

    @BeforeEach
    void setup() {
        uye = new User();
        uye.setName("Mobil Üye");
        uye.setEmail("mobil-oturum-" + UUID.randomUUID() + "@test.local");
        uye.setPasswordHash(encoder.encode(SIFRE));
        uye = users.save(uye);
        loginAttempts.reset(uye.getEmail(), "127.0.0.1");
    }

    /** Uygulamanın yaptığı gibi: CSRF al, API ile giriş yap; açılan oturumu döndür. */
    private MockHttpSession apiGiris(String sifre) throws Exception {
        MvcResult csrfSonuc = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession ilk = (MockHttpSession) csrfSonuc.getRequest().getSession(false);
        JsonNode token = mapper.readTree(csrfSonuc.getResponse().getContentAsString());
        MvcResult sonuc = mvc.perform(post("/api/v1/auth/login")
                        .session(ilk)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uye.getEmail() + "\",\"password\":\"" + sifre + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) sonuc.getRequest().getSession(false);
    }

    private JsonNode csrfJetonu(MockHttpSession oturum) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(oturum))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void api_girisi_oturumu_kayda_yazar() throws Exception {
        MockHttpSession oturum = apiGiris(SIFRE);

        assertThat(sessionRegistry.getSessionInformation(oturum.getId()))
                .as("API ile açılan oturum SessionRegistry'de olmalı")
                .isNotNull();
    }

    @Test
    void sifre_sifirlaninca_telefondaki_oturum_kapanir() throws Exception {
        MockHttpSession telefon = apiGiris(SIFRE);
        mvc.perform(get("/api/v1/me").session(telefon)).andExpect(status().isOk());

        String jeton = tokens.issue(uye, TokenType.PASSWORD_RESET);
        mvc.perform(post("/api/v1/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + jeton + "\",\"newPassword\":\"yeniSifre456\",\"confirmPassword\":\"yeniSifre456\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").session(telefon)).andExpect(status().isUnauthorized());
    }

    @Test
    void uygulamadan_sifre_degisince_tum_oturumlar_kapanir() throws Exception {
        MockHttpSession telefon = apiGiris(SIFRE);
        MockHttpSession tablet = apiGiris(SIFRE);

        JsonNode token = csrfJetonu(telefon);
        mvc.perform(post("/api/v1/me/password")
                        .session(telefon)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + SIFRE + "\",\"newPassword\":\"yeniSifre456\",\"confirmPassword\":\"yeniSifre456\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").session(tablet)).andExpect(status().isUnauthorized());
        // Web'de olduğu gibi şifreyi değiştiren oturum da kapanır; uygulama yeni şifreyle girişe yönlendirir.
        mvc.perform(get("/api/v1/me").session(telefon)).andExpect(status().isUnauthorized());
    }

    @Test
    void yonetici_izlecte_mobil_oturumu_gorur_ve_sonlandirir() throws Exception {
        MockHttpSession telefon = apiGiris(SIFRE);

        User yonetici = new User();
        yonetici.setName("İzleç Yöneticisi");
        yonetici.setEmail("izlec-yonetici-" + UUID.randomUUID() + "@test.local");
        yonetici.setPasswordHash(encoder.encode("x"));
        yonetici.setAdmin(true);
        yonetici = users.save(yonetici);
        var yoneticiKimligi = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(new AppUserDetails(yonetici));

        JsonNode izlec = mapper.readTree(mvc.perform(get("/api/v1/admin/monitor").with(yoneticiKimligi))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String tanitici = null;
        for (JsonNode s : izlec.get("sessions")) {
            if (uye.getEmail().equals(s.get("userEmail").asText())) tanitici = s.get("id").asText();
        }
        assertThat(tanitici).as("mobil oturum izleçte listelenmeli").isNotNull();

        mvc.perform(post("/api/v1/admin/monitor/sessions/" + tanitici + "/expire").with(yoneticiKimligi).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").session(telefon))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.error").exists());
    }
}
