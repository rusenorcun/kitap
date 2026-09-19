package app.kitapla.api;

import app.kitapla.api.dto.ReportBody;
import app.kitapla.api.dto.SendMessageBody;
import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageAndReportApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired ClaimRepository claims;
    @Autowired NotificationService notificationService;
    @Autowired PasswordEncoder encoder;

    private User userA;
    private User userB;
    private Claim claim;

    @BeforeEach
    void setup() {
        userA = new User();
        userA.setName("Mesajlaşan A");
        userA.setEmail("msga-" + UUID.randomUUID() + "@test.local");
        userA.setPasswordHash(encoder.encode("password"));
        userA = users.save(userA);

        userB = new User();
        userB.setName("Mesajlaşan B");
        userB.setEmail("msgb-" + UUID.randomUUID() + "@test.local");
        userB.setPasswordHash(encoder.encode("password"));
        userB = users.save(userB);

        Book b = new Book();
        b.setTitle("Sohbet Kitabı");
        b = books.save(b);

        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(b);
        d.setQuantity(1);
        d = donations.save(d);

        claim = new Claim();
        claim.setDonation(d);
        claim.setStudent(userB);
        claim = claims.save(claim);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @Test
    void conversation_and_message_flow() throws Exception {
        // User B opens conversation for claim
        MvcResult openRes = mvc.perform(get("/api/v1/conversations/open/claim/" + claim.getId())
                        .with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.counterpartName").value("Mesajlaşan A"))
                .andReturn();

        long convId = mapper.readTree(openRes.getResponse().getContentAsString()).get("id").asLong();

        // User B sends message
        SendMessageBody msgBody = new SendMessageBody("Merhaba, kitabı ne zaman teslim alabilirim?");
        mvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(msgBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(msgBody.body()))
                .andExpect(jsonPath("$.mine").value(true));

        // User A reads messages
        mvc.perform(get("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].mine").value(false));

        // User A views conversation list
        mvc.perform(get("/api/v1/conversations").with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void notifications_flow() throws Exception {
        notificationService.notify(userA, "test_notif", "Test bildirimi içeriği");

        mvc.perform(get("/api/v1/notifications").with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/v1/notifications/read-all").with(csrf()).with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", greaterThanOrEqualTo(1)));
    }

    @Autowired app.kitapla.service.ReportService reportService;

    @Test
    void report_creation_flow() throws Exception {
        ReportBody body = new ReportBody("SPAM", "Uygunsuz bağış ilanı");

        mvc.perform(post("/api/v1/reports/donation/" + claim.getDonation().getId())
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void claim_delivery_report_and_support_chat_flow() throws Exception {
        // 1. Öğrenci teslimat şikâyeti oluşturur (claim)
        ReportBody body = new ReportBody("HASARLI", "Kitap hasarlı ulaştı");

        mvc.perform(post("/api/v1/reports/claim/" + claim.getId())
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        Report r = reportService.open().stream()
                .filter(x -> x.getReporter().getId().equals(userB.getId()) && x.getKind() == ReportKind.CLAIM)
                .findFirst().orElseThrow();

        // 2. Destek sohbeti açılır (REPORT)
        MvcResult openRes = mvc.perform(get("/api/v1/conversations/open/report/" + r.getId())
                        .with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.kind").value("REPORT"))
                .andReturn();

        long convId = mapper.readTree(openRes.getResponse().getContentAsString()).get("id").asLong();

        // 3. Mesaj gönderilir
        SendMessageBody msg = new SendMessageBody("Hasar fotoğraflarını nereye gönderebilirim?");
        mvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(msg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(msg.body()));

        // 4. Yönetici mesajı okur ve yanıt verir
        User admin = new User();
        admin.setName("Admin Destek");
        admin.setEmail("adm-" + UUID.randomUUID() + "@test.local");
        admin.setPasswordHash(encoder.encode("password"));
        admin.setAdmin(true);
        admin = users.save(admin);

        mvc.perform(get("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        SendMessageBody adminMsg = new SendMessageBody("İlgileniyoruz, lütfen detay verin.");
        mvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(adminMsg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(adminMsg.body()));
    }

    /**
     * Şikâyet gönderen kendi kayıtlarını ve sonucunu görebilmeli; mobil taraf
     * "Şikâyetlerim" ekranını bu uçtan besler.
     */
    @Test
    void my_reports_listesi_kendi_kayitlarini_dondurur() throws Exception {
        ReportBody body = new ReportBody("TESLIMAT_SORUNU", "Buluşmaya gelmedi, ulaşamıyorum");

        mvc.perform(post("/api/v1/reports/claim/" + claim.getId())
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/reports").with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].kind").value("CLAIM"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].note").value(body.note()))
                .andExpect(jsonPath("$[0].reasonLabel").exists());

        // Şikâyet edilen tarafın listesinde bu kayıt görünmez
        mvc.perform(get("/api/v1/reports").with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void my_reports_girissiz_erisilemez() throws Exception {
        mvc.perform(get("/api/v1/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_rapor_listesini_gorebilir_ve_sonuclandirabilir() throws Exception {
        User admin = new User();
        admin.setName("Admin User");
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.local");
        admin.setPasswordHash(encoder.encode("admin123"));
        admin.setAdmin(true);
        admin = users.save(admin);

        ReportBody body = new ReportBody("GELMEDI", "Buluşma gerçekleşmedi");
        mvc.perform(post("/api/v1/reports/claim/" + claim.getId())
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        MvcResult result = mvc.perform(get("/api/v1/admin/reports").with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[?(@.reporterName == '" + userB.getName() + "')].reportedUserName").value(userA.getName()))
                .andReturn();

        String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var tree = mapper.readTree(content);
        long reportId = -1;
        for (var node : tree) {
            if (userB.getId().equals(node.get("reporterId").asLong())) {
                reportId = node.get("id").asLong();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(reportId).isGreaterThan(0);

        // Sonuçlandır
        var resolveBody = new app.kitapla.api.v1.AdminApiController.ResolveReportBody(true, "Gerekli uyarı yapıldı.");
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/resolve")
                        .with(user(as(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(resolveBody)))
                .andExpect(status().isNoContent());

        // Açıklar listesinde artık görünmemeli
        mvc.perform(get("/api/v1/admin/reports").with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")]").doesNotExist());

        // Tümü listesinde görünmeli
        mvc.perform(get("/api/v1/admin/reports?all=true").with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")].status").value("ACTIONED"))
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")].adminNote").value("Gerekli uyarı yapıldı."));
    }

    @Test
    void kebab_case_ve_alias_report_kind_desteklenir() throws Exception {
        ReportBody body = new ReportBody("SPAM", "Spam içeriği");
        mvc.perform(post("/api/v1/reports/delivery/" + claim.getId())
                        .with(user(as(userB)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }
}
