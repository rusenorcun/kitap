package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kitap İstekleri (Book Request) API Sözleşme Testleri")
class RequestContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/requests/open - Açık istekleri listeler ve X-Total-Count başlığı döner")
    void openRequests_returnsListAndCountHeader() throws Exception {
        MvcResult res = apiGet("/api/v1/requests/open?page=0&size=10", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getHeader("X-Total-Count")).isNotBlank();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/requests - Doğrulanmış üye kitap isteği oluşturur, 201 ve IdStatusDto döner")
    void createRequest_success_returnsCreatedIdAndStatus() throws Exception {
        AuthUser user = registerAndVerifyUser("İstek Sahibi", "req-user");

        Map<String, Object> body = Map.of(
                "title", "Algoritmalar ve Veri Yapıları",
                "author", "Thomas H. Cormen",
                "description", "Ders için acil ihtiyacım var"
        );

        MvcResult res = apiPost("/api/v1/requests", body, user.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("İstek Döngüsü: İstek açma (201), karşılama (200), buluşma (204), teslim (204), teşekkür (204)")
    void requestLifecycle_worksEndToEnd() throws Exception {
        AuthUser requester = registerAndVerifyUser("İsteyen Öğrenci", "requester");
        AuthUser fulfiller = registerAndVerifyUser("Karşılayan Bağışçı", "fulfiller");

        // İstek oluştur
        Map<String, Object> createBody = Map.of(
                "title", "Fizik 1",
                "author", "Serway",
                "description", "Mühendislik birinci sınıf ders kitabı"
        );
        MvcResult createRes = apiPost("/api/v1/requests", createBody, requester.session());
        long requestId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Kendi isteklerimde görünür
        MvcResult myReqRes = apiGet("/api/v1/my/requests", requester.session());
        assertThat(myReqRes.getResponse().getStatus()).isEqualTo(200);

        // Başka biri isteği karşılar
        MvcResult fulfillRes = apiPost("/api/v1/requests/" + requestId + "/fulfill", Map.of("source", "OWN"), fulfiller.session());
        assertThat(fulfillRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(fulfillRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("status").asText()).isEqualTo("FULFILLED");

        // Karşılayanın listesinde görünür
        MvcResult fulfilledRes = apiGet("/api/v1/my/fulfilled", fulfiller.session());
        assertThat(fulfilledRes.getResponse().getStatus()).isEqualTo(200);

        // Buluşma ayarla (204 No Content)
        String meetingTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, Object> meetingBody = Map.of(
                "at", meetingTime,
                "note", "Fakülte kantininde"
        );
        MvcResult meetingRes = apiPost("/api/v1/requests/" + requestId + "/meeting", meetingBody, fulfiller.session());
        assertThat(meetingRes.getResponse().getStatus()).isEqualTo(204);

        // Teslim et / Teslim al (204 No Content - İsteyen kullanıcı teslim aldığını onaylar)
        MvcResult deliverRes = apiPost("/api/v1/requests/" + requestId + "/deliver", null, requester.session());
        assertThat(deliverRes.getResponse().getStatus()).isEqualTo(204);

        // Teşekkür et (204 No Content)
        MvcResult thankRes = apiPost("/api/v1/requests/" + requestId + "/thank", Map.of("message", "Çok teşekkürler!"), requester.session());
        assertThat(thankRes.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("DELETE /api/v1/requests/{id} - Sahibi isteği siler (204 No Content)")
    void deleteRequest_owner_returns204() throws Exception {
        AuthUser user = registerAndVerifyUser("Silen İstekçi", "del-req");

        Map<String, Object> body = Map.of("title", "Geçici İstek", "author", "Yazar");
        MvcResult createRes = apiPost("/api/v1/requests", body, user.session());
        long requestId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult delRes = apiDelete("/api/v1/requests/" + requestId, user.session());
        assertThat(delRes.getResponse().getStatus()).isEqualTo(204);
    }
}
