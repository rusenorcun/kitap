package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Yönetim (Admin) API Sözleşme Testleri")
class AdminContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/admin/stats - Yönetici rolüyle istatistikleri 200 ile döner")
    void getStats_asAdmin_returnsStatsDto() throws Exception {
        MockHttpSession adminSession = loginUser("admin@test.local", "admin123");

        MvcResult res = apiGet("/api/v1/admin/stats", adminSession);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("totalUsers")).isTrue();
        assertThat(json.has("donations")).isTrue();
        assertThat(json.has("pendingDocs")).isTrue();
        assertThat(json.has("delivered")).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/admin/monitor - Oturum izleç verilerini 200 ile döner")
    void getMonitor_asAdmin_returnsMonitorData() throws Exception {
        MockHttpSession adminSession = loginUser("admin@test.local", "admin123");

        MvcResult res = apiGet("/api/v1/admin/monitor", adminSession);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("summary")).isTrue();
        assertThat(json.has("sessions")).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - Üye araması 200 döner")
    void searchUsers_asAdmin_returnsList() throws Exception {
        MockHttpSession adminSession = loginUser("admin@test.local", "admin123");

        MvcResult res = apiGet("/api/v1/admin/users?q=admin", adminSession);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }
}
