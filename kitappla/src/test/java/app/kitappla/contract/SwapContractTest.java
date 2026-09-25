package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Takas (Swap) API Sözleşme Testleri")
class SwapContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/swap/discover - Açık takas kitaplarını listeler ve X-Total-Count başlığı döner")
    void discover_returnsListAndCountHeader() throws Exception {
        MvcResult res = apiGet("/api/v1/swap/discover?page=0&size=10", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getHeader("X-Total-Count")).isNotBlank();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/swap/books - Doğrulanmış üye takas kitabı ekler, 201 ve IdStatusDto döner")
    void addSwapBook_success_returnsCreatedIdAndStatus() throws Exception {
        AuthUser user = registerAndVerifyUser("Takasçı 1", "swap1");

        Map<String, Object> body = Map.of(
                "title", "Suç ve Ceza",
                "author", "Fyodor Dostoyevski",
                "note", "Klasik baskı, tertemiz"
        );

        MvcResult res = apiPost("/api/v1/swap/books", body, user.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("GET /api/v1/swap/my-books - Kendi takas kitaplarını listeler")
    void getMyBooks_returnsList() throws Exception {
        AuthUser user = registerAndVerifyUser("Kitap Sahibi", "my-books");

        MvcResult res = apiGet("/api/v1/swap/my-books", user.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/swap/books/{id}/status ve DELETE - Kitap durumunu günceller ve siler (204 No Content)")
    void statusAndRemoveSwapBook_returns204() throws Exception {
        AuthUser user = registerAndVerifyUser("Kitap İşlemci", "swap-ops");

        Map<String, Object> body = Map.of(
                "title", "1984",
                "author", "George Orwell",
                "note", "Takas için açık"
        );
        MvcResult addRes = apiPost("/api/v1/swap/books", body, user.session());
        long bookId = mapper.readTree(addRes.getResponse().getContentAsString()).get("id").asLong();

        // Durumu CLOSED yap
        MvcResult statusRes = apiPost("/api/v1/swap/books/" + bookId + "/status", Map.of("status", "CLOSED"), user.session());
        assertThat(statusRes.getResponse().getStatus()).isEqualTo(204);

        // Kitabı sil
        MvcResult deleteRes = apiDelete("/api/v1/swap/books/" + bookId, user.session());
        assertThat(deleteRes.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("POST /api/v1/swap/books/{id}/to-donation - Takas kitabını bağışa aktarır ve IdStatusDto döner")
    void moveToDonation_returnsIdStatusDto() throws Exception {
        AuthUser user = registerAndVerifyUser("Aktarıcı", "swap-to-don");

        Map<String, Object> body = Map.of(
                "title", "Körlük",
                "author", "Jose Saramago"
        );
        MvcResult addRes = apiPost("/api/v1/swap/books", body, user.session());
        long bookId = mapper.readTree(addRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult moveRes = apiPost("/api/v1/swap/books/" + bookId + "/to-donation", null, user.session());
        assertThat(moveRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(moveRes.getResponse().getContentAsString());
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Takas Teklif Döngüsü: Teklif verme (201), listeleme (200), kabul etme (200), buluşma (204)")
    void swapOfferLifecycle_worksEndToEnd() throws Exception {
        AuthUser userA = registerAndVerifyUser("Kullanıcı A", "usr-a");
        AuthUser userB = registerAndVerifyUser("Kullanıcı B", "usr-b");

        // A kitabı ekler
        MvcResult addResA = apiPost("/api/v1/swap/books", Map.of("title", "Nutuk", "author", "Atatürk"), userA.session());
        long bookAId = mapper.readTree(addResA.getResponse().getContentAsString()).get("id").asLong();

        // B kitabı ekler
        MvcResult addResB = apiPost("/api/v1/swap/books", Map.of("title", "Çalıkuşu", "author", "Reşat Nuri Güntekin"), userB.session());
        long bookBId = mapper.readTree(addResB.getResponse().getContentAsString()).get("id").asLong();

        // B, A'nın kitabına teklif verir
        Map<String, Object> offerBody = Map.of(
                "targetBookId", bookAId,
                "offeredBookId", bookBId,
                "message", "Takas edelim mi?"
        );
        MvcResult offerRes = apiPost("/api/v1/swaps", offerBody, userB.session());
        assertThat(offerRes.getResponse().getStatus()).isEqualTo(201);
        long offerId = mapper.readTree(offerRes.getResponse().getContentAsString()).get("id").asLong();

        // A gelen tekliflerde görür
        MvcResult incRes = apiGet("/api/v1/swaps/incoming", userA.session());
        assertThat(incRes.getResponse().getStatus()).isEqualTo(200);

        // B giden tekliflerde görür
        MvcResult outRes = apiGet("/api/v1/swaps/outgoing", userB.session());
        assertThat(outRes.getResponse().getStatus()).isEqualTo(200);

        // A teklifi kabul eder
        MvcResult acceptRes = apiPost("/api/v1/swaps/" + offerId + "/accept", null, userA.session());
        assertThat(acceptRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(acceptRes.getResponse().getContentAsString()).get("status").asText()).isEqualTo("ACCEPTED");

        // Buluşma ayarlanır (204 No Content)
        String meetingTime = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, Object> meetingBody = Map.of(
                "at", meetingTime,
                "note", "Kütüphane önünde buluşalım"
        );
        MvcResult meetingRes = apiPost("/api/v1/swaps/" + offerId + "/meeting", meetingBody, userA.session());
        assertThat(meetingRes.getResponse().getStatus()).isEqualTo(204);

        // Teslim edildi bildirimi (handover) 204 No Content döner
        MvcResult handoverRes = apiPost("/api/v1/swaps/" + offerId + "/handover", null, userA.session());
        assertThat(handoverRes.getResponse().getStatus()).isEqualTo(204);
    }
}
