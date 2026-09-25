package app.kitappla.api;

import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.security.SessionTokenService;
import app.kitappla.service.AdminMonitorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin oturum izleci API'si testleri:
 * - Yanıtta ham oturum kimliği sızdırılmadığını doğrular
 * - currentSession bayrağının doğru işaretlendiğini doğrular
 * - Başka bir oturumu sonlandırma 204 döndürür ve hedef oturum 401 alır
 * - Kendi oturumunu sonlandırma 400 döndürür
 * - Bilinmeyen tanıtıcı 404 döndürür
 * - Üye (admin olmayan) 403 alır
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMonitorSessionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionRegistry sessionRegistry;
    @Autowired AdminMonitorService monitorService;
    @Autowired SessionTokenService sessionTokenService;

    private User adminUser;
    private User normalUser;
    private User otherAdmin;

    @BeforeEach
    void setup() {
        adminUser = mkUser("admin-monitor-" + UUID.randomUUID(), true);
        normalUser = mkUser("uye-monitor-" + UUID.randomUUID(), false);
        otherAdmin = mkUser("other-admin-" + UUID.randomUUID(), true);
    }

    private User mkUser(String tag, boolean admin) {
        User u = new User();
        u.setName("Test " + tag);
        u.setEmail(tag + "@test.local");
        u.setPasswordHash(encoder.encode("password"));
        u.setAdmin(admin);
        return users.save(u);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    // ---------- Yanıtta ham oturum kimliği yok ----------

    @Test
    void monitor_response_does_not_leak_raw_session_id() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AppUserDetails principal = as(adminUser);

        // Oturumu kaydettir
        sessionRegistry.registerNewSession(session.getId(), principal);

        MvcResult result = mvc.perform(get("/api/v1/admin/monitor")
                        .session(session)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode root = mapper.readTree(body);

        for (JsonNode s : root.get("sessions")) {
            // "id" alanı ham oturum kimliği olmamalı
            String id = s.get("id").asText();
            assertThat(id).as("id alanı ham oturum kimliği olmamalı").isNotEqualTo(session.getId());
            // Opak token UUID formatında olmalı
            assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            // sessionId alanı artık olmamalı (eski alan adı)
            assertThat(s.has("sessionId")).as("sessionId alanı olmamalı").isFalse();
        }

        sessionRegistry.removeSessionInformation(session.getId());
    }

    // ---------- currentSession doğru işaretleniyor ----------

    @Test
    void current_session_is_correctly_flagged() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();
        AppUserDetails adminPrincipal = as(adminUser);
        AppUserDetails otherPrincipal = as(otherAdmin);

        sessionRegistry.registerNewSession(adminSession.getId(), adminPrincipal);
        sessionRegistry.registerNewSession(otherSession.getId(), otherPrincipal);

        MvcResult result = mvc.perform(get("/api/v1/admin/monitor")
                        .session(adminSession)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        boolean foundCurrent = false;
        boolean foundOther = false;
        for (JsonNode s : root.get("sessions")) {
            // Resolve the opaque ID to find which is which
            String opaqueId = s.get("id").asText();
            String realId = sessionTokenService.resolve(opaqueId);
            if (adminSession.getId().equals(realId)) {
                assertThat(s.get("currentSession").asBoolean()).isTrue();
                foundCurrent = true;
            }
            if (otherSession.getId().equals(realId)) {
                assertThat(s.get("currentSession").asBoolean()).isFalse();
                foundOther = true;
            }
        }
        assertThat(foundCurrent).as("Geçerli oturum bulunmalı").isTrue();
        assertThat(foundOther).as("Diğer oturum bulunmalı").isTrue();

        sessionRegistry.removeSessionInformation(adminSession.getId());
        sessionRegistry.removeSessionInformation(otherSession.getId());
    }

    // ---------- Başka bir oturumu sonlandırma 204 ----------

    @Test
    void expire_other_session_returns_204_and_target_gets_401() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        MockHttpSession targetSession = new MockHttpSession();
        AppUserDetails adminPrincipal = as(adminUser);
        AppUserDetails targetPrincipal = as(otherAdmin);

        sessionRegistry.registerNewSession(adminSession.getId(), adminPrincipal);
        sessionRegistry.registerNewSession(targetSession.getId(), targetPrincipal);

        // Hedef oturumun opak token'ını al
        String targetToken = sessionTokenService.tokenFor(targetSession.getId());

        // Sonlandır
        mvc.perform(post("/api/v1/admin/monitor/sessions/" + targetToken + "/expire")
                        .session(adminSession)
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Hedef oturum artık süresi dolmuş olmalı
        var info = sessionRegistry.getSessionInformation(targetSession.getId());
        assertThat(info).isNotNull();
        assertThat(info.isExpired()).isTrue();

        sessionRegistry.removeSessionInformation(adminSession.getId());
        sessionRegistry.removeSessionInformation(targetSession.getId());
    }

    // ---------- Kendi oturumunu sonlandırma 400 ----------

    @Test
    void expire_own_session_returns_400() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AppUserDetails principal = as(adminUser);

        sessionRegistry.registerNewSession(session.getId(), principal);

        // Kendi oturumunun opak token'ını al
        String ownToken = sessionTokenService.tokenFor(session.getId());

        mvc.perform(post("/api/v1/admin/monitor/sessions/" + ownToken + "/expire")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Kendi geçerli oturumunu sonlandıramazsın."));

        // Oturum hâlâ aktif olmalı
        var info = sessionRegistry.getSessionInformation(session.getId());
        assertThat(info).isNotNull();
        assertThat(info.isExpired()).isFalse();

        sessionRegistry.removeSessionInformation(session.getId());
    }

    // ---------- Bilinmeyen tanıtıcı 404 ----------

    @Test
    void expire_unknown_token_returns_404() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AppUserDetails principal = as(adminUser);

        mvc.perform(post("/api/v1/admin/monitor/sessions/non-existent-token/expire")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Oturum bulunamadı."));
    }

    // ---------- Üye 403 ----------

    @Test
    void normal_user_cannot_expire_session_returns_403() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AppUserDetails principal = as(normalUser);

        mvc.perform(post("/api/v1/admin/monitor/sessions/any-token/expire")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void normal_user_cannot_access_monitor_returns_403() throws Exception {
        mvc.perform(get("/api/v1/admin/monitor")
                        .with(user(as(normalUser))))
                .andExpect(status().isForbidden());
    }
}
