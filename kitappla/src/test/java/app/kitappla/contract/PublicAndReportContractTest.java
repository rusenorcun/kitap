package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Genel, Rapor ve Sistem API Sözleşme Testleri")
class PublicAndReportContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/features - Aktif özellikleri döner")
    void getFeatures_returnsFeaturesDto() throws Exception {
        MvcResult res = apiGet("/api/v1/features", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("shipping")).isTrue();
        assertThat(json.has("purchase")).isTrue();
        assertThat(json.has("address")).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/pickup-points - Aktif teslim noktalarını listeler")
    void getPickupPoints_returnsList() throws Exception {
        MvcResult res = apiGet("/api/v1/pickup-points", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/books - Kitap arama listesini döner")
    void searchBooks_returnsList() throws Exception {
        MvcResult res = apiGet("/api/v1/books?q=a", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("Şikayet Döngüsü: Şikayet oluşturma (204) ve kendi şikayetlerini listeleme (200)")
    void reportLifecycle_works() throws Exception {
        AuthUser reporter = registerAndVerifyUser("Şikayetçi Üye", "reporter");
        AuthUser reported = registerAndVerifyUser("İlan Sahibi", "reported");

        MvcResult donRes = apiPost("/api/v1/donations", Map.of("title", "Şikayet Edilecek İlan", "quantity", 1, "source", "OWN"), reported.session());
        long donId = mapper.readTree(donRes.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> body = Map.of(
                "reason", "UYGUNSUZ",
                "note", "Uygunsuz içerik barındırıyor"
        );

        MvcResult reportRes = apiPost("/api/v1/reports/donation/" + donId, body, reporter.session());
        assertThat(reportRes.getResponse().getStatus()).isEqualTo(204);

        MvcResult listRes = apiGet("/api/v1/reports", reporter.session());
        assertThat(listRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode listJson = mapper.readTree(listRes.getResponse().getContentAsString());
        assertThat(listJson.isArray()).isTrue();
        assertThat(listJson.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("GET /.well-known/assetlinks.json - Android App Link doğrulama dosyasını 200 ile döner")
    void assetLinks_returns200AndValidJson() throws Exception {
        MvcResult res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/.well-known/assetlinks.json"))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }
}
