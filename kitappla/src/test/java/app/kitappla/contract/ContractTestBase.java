package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bağımsız API Sözleşme Testleri Temel Sınıfı.
 * <p>
 * Bu test sınıfı kaynak kodların iç implementasyonuna (servis mock'ları, JPA sorgu detayları vb.)
 * BAĞIMLI DEĞİLDİR. Yalnızca HTTP üzerinden gelen ve giden verileri (Girdi / Çıktı) referans alır.
 * Gerçek bir mobil uygulama veya web istemcisi gibi istek atar, JSON yanıtlarını ve durum kodlarını doğrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ContractTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected app.kitappla.repo.UserRepository userRepository;

    protected record CsrfInfo(String token, String headerName, String parameterName) {}

    protected record AuthUser(String email, String password, String name, MockHttpSession session, Long id) {}

    /**
     * E-postası onaylanmış bir kullanıcı oluşturur ve oturumunu döner (durum değiştiren işlemler için).
     */
    protected AuthUser registerAndVerifyUser(String name, String emailPrefix) throws Exception {
        AuthUser user = registerNewUser(name, emailPrefix);
        userRepository.findById(user.id()).ifPresent(u -> {
            u.setEmailVerified(true);
            userRepository.save(u);
        });
        return user;
    }

    /**
     * Doğrulanmış ve onaylı öğrenci kullanıcısı oluşturur (bağış öncelik süresine takılmadan talep edebilsin diye).
     */
    protected AuthUser registerAndApproveStudent(String name, String emailPrefix) throws Exception {
        AuthUser user = registerAndVerifyUser(name, emailPrefix);
        userRepository.findById(user.id()).ifPresent(u -> {
            u.setStudentStatus(app.kitappla.domain.StudentStatus.APPROVED);
            userRepository.save(u);
        });
        return user;
    }

    /**
     * API'den CSRF jetonu alır (tıpkı mobil uygulamanın CsrfInterceptor'ı gibi).
     */
    protected CsrfInfo fetchCsrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/v1/auth/csrf");
        if (session != null) req.session(session);

        MvcResult res = mvc.perform(req)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        return new CsrfInfo(
                json.get("token").asText(),
                json.get("headerName").asText(),
                json.get("parameterName").asText()
        );
    }

    /**
     * Tamamen HTTP POST /api/v1/auth/register çağrısıyla yeni bir kullanıcı oluşturur ve oturumunu döner.
     */
    protected AuthUser registerNewUser(String name, String emailPrefix) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        String email = emailPrefix + "-" + UUID.randomUUID() + "@test.local";
        String password = "password123";

        Map<String, Object> body = Map.of(
                "name", name,
                "email", email,
                "password", password,
                "school", "ATATURK_UNIVERSITESI",
                "phone", "05551234567"
        );

        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode resJson = mapper.readTree(res.getResponse().getContentAsString());
        Long id = resJson.get("user").get("id").asLong();

        // Giriş sonrası dönen yeni oturumu al
        MockHttpSession authSession = (MockHttpSession) res.getRequest().getSession(false);
        if (authSession == null) authSession = session;

        return new AuthUser(email, password, name, authSession, id);
    }

    /**
     * HTTP POST /api/v1/auth/login çağrısıyla giriş yapar ve doğrulanmış oturum döner.
     */
    protected MockHttpSession loginUser(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfInfo csrf = fetchCsrf(session);

        Map<String, String> body = Map.of("email", email, "password", password);

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authSession = (MockHttpSession) res.getRequest().getSession(false);
        return authSession != null ? authSession : session;
    }

    /**
     * API'ye GET isteği atar.
     */
    protected MvcResult apiGet(String url, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder req = get(url).contentType(MediaType.APPLICATION_JSON);
        if (session != null) req.session(session);
        return mvc.perform(req).andReturn();
    }

    /**
     * API'ye CSRF korumalı POST isteği atar.
     */
    protected MvcResult apiPost(String url, Object body, MockHttpSession session) throws Exception {
        CsrfInfo csrf = fetchCsrf(session);
        MockHttpServletRequestBuilder req = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(csrf.headerName(), csrf.token());
        if (session != null) req.session(session);
        if (body != null) {
            String content = body instanceof String s ? s : mapper.writeValueAsString(body);
            req.content(content);
        }
        return mvc.perform(req).andReturn();
    }

    /**
     * API'ye CSRF korumalı PUT isteği atar.
     */
    protected MvcResult apiPut(String url, Object body, MockHttpSession session) throws Exception {
        CsrfInfo csrf = fetchCsrf(session);
        MockHttpServletRequestBuilder req = put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(csrf.headerName(), csrf.token());
        if (session != null) req.session(session);
        if (body != null) {
            String content = body instanceof String s ? s : mapper.writeValueAsString(body);
            req.content(content);
        }
        return mvc.perform(req).andReturn();
    }

    /**
     * API'ye CSRF korumalı DELETE isteği atar.
     */
    protected MvcResult apiDelete(String url, MockHttpSession session) throws Exception {
        CsrfInfo csrf = fetchCsrf(session);
        MockHttpServletRequestBuilder req = delete(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(csrf.headerName(), csrf.token());
        if (session != null) req.session(session);
        return mvc.perform(req).andReturn();
    }

    /**
     * JSON yanıtındaki hata nesnesini ve hem error hem message alanlarını doğrular.
     */
    protected void assertApiError(MvcResult res, int expectedStatus, String expectedMessageContains, String expectedCode) throws Exception {
        assertThat(res.getResponse().getStatus()).isEqualTo(expectedStatus);
        String body = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).isNotBlank();

        JsonNode json = mapper.readTree(body);
        assertThat(json.has("error")).isTrue();
        assertThat(json.has("message")).isTrue();

        String err = json.get("error").asText();
        String msg = json.get("message").asText();

        // error ve message birbirine eşit olmalıdır (mobil/web uyumu)
        assertThat(err).isEqualTo(msg);

        if (expectedMessageContains != null) {
            assertThat(err).contains(expectedMessageContains);
        }

        if (expectedCode != null) {
            assertThat(json.has("code")).isTrue();
            assertThat(json.get("code").asText()).isEqualTo(expectedCode);
        }
    }
}
