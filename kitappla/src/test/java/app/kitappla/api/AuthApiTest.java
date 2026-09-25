package app.kitappla.api;

import app.kitappla.api.dto.LoginBody;
import app.kitappla.api.dto.RegisterBody;
import app.kitappla.domain.TokenType;
import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokenService;
    @Autowired LoginAttemptService loginAttempts;

    private User testUser;
    private String rawPassword = "password123";

    @BeforeEach
    void setup() {
        String email = "api-auth-" + UUID.randomUUID() + "@test.local";
        testUser = new User();
        testUser.setName("API Auth Test");
        testUser.setEmail(email);
        testUser.setPasswordHash(encoder.encode(rawPassword));
        testUser = users.save(testUser);
        loginAttempts.reset(email, "127.0.0.1");
    }

    @Test
    void login_success_returns_MeDto_and_sets_session() throws Exception {
        LoginBody body = new LoginBody(testUser.getEmail(), rawPassword);

        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession initialSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String initialSessionId = initialSession.getId();
        JsonNode token = mapper.readTree(csrfResult.getResponse().getContentAsString());
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .session(initialSession)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.user.name").value("API Auth Test"))
                .andExpect(jsonPath("$.quota.tier").value("member"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(initialSessionId);
        assertTokenRotated(session, token);

        // Check that subsequent request with this session succeeds on /api/v1/me
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(testUser.getEmail()));
    }

    @Test
    void login_invalid_password_returns_400() throws Exception {
        LoginBody body = new LoginBody(testUser.getEmail(), "wrong-password");

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("E-posta ya da şifre hatalı."));
    }

    @Test
    void register_success_creates_user_and_logs_in() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.local";
        RegisterBody body = new RegisterBody("Yeni Üye", email, "secret123", "ATATURK_UNIVERSITESI", null, "5551112233", null);

        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession initialSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String initialSessionId = initialSession.getId();
        JsonNode token = mapper.readTree(csrfResult.getResponse().getContentAsString());
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .session(initialSession)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Student-Verification-Sent", "false"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value("Yeni Üye"))
                .andExpect(jsonPath("$.user.school").value("ATATURK_UNIVERSITESI"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(initialSessionId);
        assertTokenRotated(session, token);
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void forgot_and_reset_password_flow() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testUser.getEmail() + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        String token = tokenService.issue(testUser, TokenType.PASSWORD_RESET);

        mvc.perform(post("/api/v1/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"newSecret123\",\"confirmPassword\":\"newSecret123\"}"))
                .andExpect(status().isNoContent());

        // Login with new password
        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginBody(testUser.getEmail(), "newSecret123"))))
                .andExpect(status().isOk());
    }

    @Test
    void logout_invalidates_session() throws Exception {
        LoginBody body = new LoginBody(testUser.getEmail(), rawPassword);
        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession initialSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String initialSessionId = initialSession.getId();
        JsonNode token = mapper.readTree(csrfResult.getResponse().getContentAsString());
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .session(initialSession)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        assertThat(session.getId()).isNotEqualTo(initialSessionId);
        mvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();

        JsonNode refreshed = mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/logout").session(session)
                        .header(refreshed.get("headerName").asText(), refreshed.get("token").asText()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    private void assertTokenRotated(MockHttpSession session, JsonNode previous) throws Exception {
        mvc.perform(post("/api/v1/notifications/read-all").session(session)
                        .header(previous.get("headerName").asText(), previous.get("token").asText()))
                .andExpect(status().isForbidden());
        JsonNode refreshed = mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/notifications/read-all").session(session)
                        .header(refreshed.get("headerName").asText(), refreshed.get("token").asText()))
                .andExpect(status().isOk());
    }
}
