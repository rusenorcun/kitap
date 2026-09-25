package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Bağış ve Talep (Donation & Claim) API Sözleşme Testleri")
class DonationContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/donations - Açık bağışları listeler ve X-Total-Count başlığı döner")
    void getDonations_returnsListAndCountHeader() throws Exception {
        MvcResult res = apiGet("/api/v1/donations?page=0&size=10", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getHeader("X-Total-Count")).isNotBlank();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/donations - Doğrulanmış üye bağış oluşturur, 201 ve IdStatusDto döner")
    void createDonation_success_returnsCreatedIdAndStatus() throws Exception {
        AuthUser donor = registerAndVerifyUser("Bağışçı", "donor");

        Map<String, Object> body = Map.of(
                "title", "Kürk Mantolu Madonna",
                "author", "Sabahattin Ali",
                "quantity", 2,
                "targetLevel", "HEPSI",
                "source", "OWN",
                "description", "Temiz durumda iki adet kitap"
        );

        MvcResult res = apiPost("/api/v1/donations", body, donor.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("GET /api/v1/donations/{id} - Var olan bağışın detayını ve DTO alanlarını döner")
    void getDonationDetail_existing_returnsDonationDto() throws Exception {
        AuthUser donor = registerAndVerifyUser("Detay Bağışçı", "detail-donor");

        Map<String, Object> body = Map.of(
                "title", "Sefiller",
                "author", "Victor Hugo",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult createRes = apiPost("/api/v1/donations", body, donor.session());
        long donationId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult detailRes = apiGet("/api/v1/donations/" + donationId, donor.session());
        assertThat(detailRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(detailRes.getResponse().getContentAsString());
        assertThat(json.get("id").asLong()).isEqualTo(donationId);
        assertThat(json.get("book").get("title").asText()).isEqualTo("Sefiller");
        assertThat(json.get("mine").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/donations/{id} - Olmayan bağış ID'sinde 404 NOT_FOUND döner")
    void getDonationDetail_notFound_returns404() throws Exception {
        MvcResult res = apiGet("/api/v1/donations/99999999", null);
        assertApiError(res, 404, "Bağış bulunamadı", null);
    }

    @Test
    @DisplayName("POST /api/v1/donations/{id}/claim - Uygun üye kitabı talep eder ve ClaimDto döner")
    void claimDonation_success_returnsClaimDto() throws Exception {
        AuthUser donor = registerAndVerifyUser("Bağışçı 2", "donor2");
        AuthUser receiver = registerAndApproveStudent("Alıcı Öğrenci", "rec-stu");

        Map<String, Object> body = Map.of(
                "title", "Simyacı",
                "author", "Paulo Coelho",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult createRes = apiPost("/api/v1/donations", body, donor.session());
        long donationId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult claimRes = apiPost("/api/v1/donations/" + donationId + "/claim", null, receiver.session());
        assertThat(claimRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(claimRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("MATCHED");
        assertThat(json.get("requesterName").asText()).isEqualTo(receiver.name());
    }

    @Test
    @DisplayName("POST /api/v1/donations/{id}/close ve /reopen - Bağış sahibi bağışı kapatır ve yeniden açar (204 No Content)")
    void closeAndReopenDonation_owner_returns204() throws Exception {
        AuthUser donor = registerAndVerifyUser("Kapatıcı Bağışçı", "close-donor");

        Map<String, Object> body = Map.of(
                "title", "Dönüşüm",
                "author", "Franz Kafka",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult createRes = apiPost("/api/v1/donations", body, donor.session());
        long donationId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Kapatma
        MvcResult closeRes = apiPost("/api/v1/donations/" + donationId + "/close", null, donor.session());
        assertThat(closeRes.getResponse().getStatus()).isEqualTo(204);

        // Yeniden açma
        MvcResult reopenRes = apiPost("/api/v1/donations/" + donationId + "/reopen", null, donor.session());
        assertThat(reopenRes.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("DELETE /api/v1/donations/{id} - Bağış sahibi bağışı siler (204 No Content)")
    void deleteDonation_owner_returns204() throws Exception {
        AuthUser donor = registerAndVerifyUser("Silen Bağışçı", "del-donor");

        Map<String, Object> body = Map.of(
                "title", "Yabancı",
                "author", "Albert Camus",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult createRes = apiPost("/api/v1/donations", body, donor.session());
        long donationId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult delRes = apiDelete("/api/v1/donations/" + donationId, donor.session());
        assertThat(delRes.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("POST /api/v1/donations/{id}/to-swap - Bağışı takasa aktarır ve IdStatusDto döner")
    void moveToSwap_owner_returnsIdStatusDto() throws Exception {
        AuthUser donor = registerAndVerifyUser("Takasa Aktaran", "swap-move");

        Map<String, Object> body = Map.of(
                "title", "Satranç",
                "author", "Stefan Zweig",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult createRes = apiPost("/api/v1/donations", body, donor.session());
        long donationId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult moveRes = apiPost("/api/v1/donations/" + donationId + "/to-swap", Map.of("note", "Takas notu"), donor.session());
        assertThat(moveRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(moveRes.getResponse().getContentAsString());
        assertThat(json.has("id")).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("GET /api/v1/my/donations ve /my/claims - Kendi bağışlarımı ve taleplerimi listeler")
    void getMyDonationsAndClaims_returnsLists() throws Exception {
        AuthUser user = registerAndVerifyUser("Listeleme Üyesi", "my-lists");

        MvcResult donRes = apiGet("/api/v1/my/donations", user.session());
        assertThat(donRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode donJson = mapper.readTree(donRes.getResponse().getContentAsString());
        assertThat(donJson.isArray()).isTrue();

        MvcResult claimRes = apiGet("/api/v1/my/claims", user.session());
        assertThat(claimRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode claimJson = mapper.readTree(claimRes.getResponse().getContentAsString());
        assertThat(claimJson.isArray()).isTrue();
    }
}
