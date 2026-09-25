package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kimlik Doğrulama ve Giriş API Sözleşme Testleri")
class AuthContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/auth/csrf - CSRF jetonu, başlık ve parametre adını içeren JSON döner")
    void getCsrf_returnsTokenAndHeaders() throws Exception {
        CsrfInfo csrf = fetchCsrf(null);
        assertThat(csrf.token()).isNotBlank();
        assertThat(csrf.headerName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.parameterName()).isEqualTo("_csrf");
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Başarılı kayıt kullanıcı ve kota bilgisiyle 201 döner")
    void register_success_returnsCreatedUserAndQuota() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        String email = "yeni-uye-" + UUID.randomUUID() + "@test.local";
        Map<String, Object> body = Map.of(
                "name", "Ahmet Yılmaz",
                "email", email,
                "password", "sifre12345",
                "school", "ATATURK_UNIVERSITESI",
                "phone", "05559876543"
        );

        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Email-Verification-Sent"))
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(json.has("user")).isTrue();
        assertThat(json.has("quota")).isTrue();

        JsonNode user = json.get("user");
        assertThat(user.get("name").asText()).isEqualTo("Ahmet Yılmaz");
        assertThat(user.get("email").asText()).isEqualTo(email);
        assertThat(user.get("emailVerified").asBoolean()).isTrue();

        JsonNode quota = json.get("quota");
        assertThat(quota.get("tier").asText()).isEqualTo("member");
        assertThat(quota.get("weeklyRemaining").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Geçersiz form verisinde (kısa şifre / geçersiz email) 400 ve açık hata döner")
    void register_invalidForm_returns400WithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        Map<String, Object> body = Map.of(
                "name", "",
                "email", "gecersiz-eposta",
                "password", "123",
                "school", "ATATURK_UNIVERSITESI"
        );

        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertApiError(res, 400, null, null);
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Mükerrer e-posta ile kayıtta 400 döner")
    void register_duplicateEmail_returns400() throws Exception {
        AuthUser user1 = registerNewUser("İlk Kullanıcı", "dup");

        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        Map<String, Object> body = Map.of(
                "name", "İkinci Kullanıcı",
                "email", user1.email(),
                "password", "sifre12345",
                "school", "ATATURK_UNIVERSITESI"
        );

        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertApiError(res, 400, "Bu e-posta zaten kayıtlı", null);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Doğru bilgilerle giriş 200 döner ve oturum başlatır")
    void login_success_returnsMeDto() throws Exception {
        AuthUser user = registerNewUser("Giriş Test", "log");

        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        Map<String, String> body = Map.of("email", user.email(), "password", user.password());

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("user")).isTrue();
        assertThat(json.get("user").get("email").asText()).isEqualTo(user.email());

        MockHttpSession newSession = (MockHttpSession) res.getRequest().getSession(false);
        assertThat(newSession).isNotNull();

        // Oturumla /api/v1/me çağrısı başarılı olmalıdır
        MvcResult meRes = apiGet("/api/v1/me", newSession);
        assertThat(meRes.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Hatalı şifre 400 döner")
    void login_wrongPassword_returns400() throws Exception {
        AuthUser user = registerNewUser("Şifre Test", "pwd");

        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        Map<String, String> body = Map.of("email", user.email(), "password", "yanlis_sifre");

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertApiError(res, 400, "E-posta ya da şifre hatalı", null);
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - Oturumu sonlandırır ve 204 döner")
    void logout_terminatesSession() throws Exception {
        AuthUser user = registerNewUser("Çıkış Test", "lgo");

        MvcResult res = apiPost("/api/v1/auth/logout", null, user.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(204);

        // Oturum geçersiz kılınmış olmalıdır
        assertThat(user.session().isInvalid()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password - Kayıtlı veya kayıtsız e-posta için 202 döner (zamanlama güvenliği)")
    void forgotPassword_returnsAccepted() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> body = Map.of("email", "herhangi-biri@test.local");

        MvcResult res = apiPost("/api/v1/auth/forgot-password", body, session);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("message")).isTrue();
    }
}
