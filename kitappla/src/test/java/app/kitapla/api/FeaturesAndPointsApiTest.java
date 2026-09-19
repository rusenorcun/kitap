package app.kitapla.api;

import app.kitapla.domain.PickupPoint;
import app.kitapla.repo.PickupPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeaturesAndPointsApiTest {

    @Autowired MockMvc mvc;
    @Autowired PickupPointRepository points;

    @BeforeEach
    void setup() {
        if (points.count() == 0) {
            PickupPoint p = new PickupPoint();
            p.setCampus("Atatürk Üniversitesi");
            p.setName("Merkezi Kütüphane");
            p.setDescription("Giriş kat");
            p.setActive(true);
            points.save(p);
        }
    }

    @Test
    void get_features_returns_flags() throws Exception {
        mvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping").isBoolean())
                .andExpect(jsonPath("$.purchase").isBoolean())
                .andExpect(jsonPath("$.address").isBoolean())
                .andExpect(jsonPath("$.messaging").value(true))
                .andExpect(jsonPath("$.reports").value(true));
    }

    @Test
    void get_pickup_points_returns_active_points() throws Exception {
        mvc.perform(get("/api/v1/pickup-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].active").value(true));
    }
}
