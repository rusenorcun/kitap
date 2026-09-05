package app.kitapla.api;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User normalUser;
    private User adminUser;

    @BeforeEach
    void setup() {
        normalUser = new User();
        normalUser.setName("Normal Üye");
        normalUser.setEmail("norm-" + UUID.randomUUID() + "@test.local");
        normalUser.setPasswordHash(encoder.encode("password"));
        normalUser.setAdmin(false);
        normalUser = users.save(normalUser);

        adminUser = new User();
        adminUser.setName("Admin Üye");
        adminUser.setEmail("admin-" + UUID.randomUUID() + "@test.local");
        adminUser.setPasswordHash(encoder.encode("password"));
        adminUser.setAdmin(true);
        adminUser = users.save(adminUser);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @Test
    void unauthenticated_request_to_protected_api_returns_401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/my/donations"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_endpoint_forbidden_for_normal_user_returns_403() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").with(user(as(normalUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_endpoint_accessible_for_admin_user_returns_200() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").with(user(as(adminUser))))
                .andExpect(status().isOk());
    }
}
