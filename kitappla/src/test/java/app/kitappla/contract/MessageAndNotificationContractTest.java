package app.kitappla.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mesajlaşma ve Bildirim API Sözleşme Testleri")
class MessageAndNotificationContractTest extends ContractTestBase {

    @Test
    @DisplayName("GET /api/v1/conversations - Oturum açmış üyenin sohbetlerini listeler")
    void conversations_authenticated_returnsList() throws Exception {
        AuthUser user = registerAndVerifyUser("Sohbet Üyesi", "chat-user");

        MvcResult res = apiGet("/api/v1/conversations", user.session());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
    }

    @Test
    @DisplayName("Mesaj Gönderme ve Listeleme: Bağış talebi sonrası sohbet açma, mesaj gönderme (201) ve mesajları okuma (200)")
    void chatMessageLifecycle_worksEndToEnd() throws Exception {
        AuthUser donor = registerAndVerifyUser("Bağışçı Sohbet", "donor-chat");
        AuthUser claimer = registerAndApproveStudent("Alıcı Sohbet", "claimer-chat");

        // Bağış ve talep oluştur
        Map<String, Object> donBody = Map.of(
                "title", "Mesajlaşma Kitabı",
                "author", "Yazar Adı",
                "quantity", 1,
                "targetLevel", "HEPSI",
                "source", "OWN"
        );
        MvcResult donRes = apiPost("/api/v1/donations", donBody, donor.session());
        long donId = mapper.readTree(donRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult claimRes = apiPost("/api/v1/donations/" + donId + "/claim", null, claimer.session());
        assertThat(claimRes.getResponse().getStatus()).isEqualTo(200);
        long claimId = mapper.readTree(claimRes.getResponse().getContentAsString()).get("id").asLong();

        // Sohbeti aç
        MvcResult openRes = apiGet("/api/v1/conversations/open/CLAIM/" + claimId, claimer.session());
        assertThat(openRes.getResponse().getStatus()).isEqualTo(200);
        long convId = mapper.readTree(openRes.getResponse().getContentAsString()).get("id").asLong();

        // Mesaj gönder (201 Created)
        Map<String, Object> msgBody = Map.of("body", "Merhaba, kitabı ne zaman alabilirim?");
        MvcResult sendRes = apiPost("/api/v1/conversations/" + convId + "/messages", msgBody, claimer.session());
        assertThat(sendRes.getResponse().getStatus()).isEqualTo(201);

        JsonNode sendJson = mapper.readTree(sendRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(sendJson.has("id")).isTrue();
        assertThat(sendJson.get("body").asText()).isEqualTo("Merhaba, kitabı ne zaman alabilirim?");
        assertThat(sendJson.get("mine").asBoolean()).isTrue();

        // Karşı taraf mesajları listeler
        MvcResult listRes = apiGet("/api/v1/conversations/" + convId + "/messages", donor.session());
        assertThat(listRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode listJson = mapper.readTree(listRes.getResponse().getContentAsString());
        assertThat(listJson.isArray()).isTrue();
        assertThat(listJson.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Bildirimler: Listeleme (200), unreadCount alanı ve toplu okundu yapma (read-all)")
    void notifications_readAndReadAll() throws Exception {
        AuthUser user = registerAndVerifyUser("Bildirim Üyesi", "notif-user");

        // Bildirimleri al
        MvcResult notifRes = apiGet("/api/v1/notifications", user.session());
        assertThat(notifRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = mapper.readTree(notifRes.getResponse().getContentAsString());
        assertThat(json.has("items")).isTrue();
        assertThat(json.has("unread")).isTrue();

        // Tümünü okundu yap
        MvcResult readAllRes = apiPost("/api/v1/notifications/read-all", null, user.session());
        assertThat(readAllRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode readAllJson = mapper.readTree(readAllRes.getResponse().getContentAsString());
        assertThat(readAllJson.has("updated")).isTrue();
    }
}
