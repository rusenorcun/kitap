package app.kitappla.api;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** İstemci kaynaklı istek hataları (eksik parça/parametre) 500 değil 4xx döner. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiHataKoduTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void eksikDosyaParcasi400Doner() throws Exception {
        User u = new User();
        u.setName("Hata Kodu");
        u.setEmail("hata-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        users.save(u);

        mvc.perform(multipart("/api/v1/uploads").with(user(new AppUserDetails(u))).with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
