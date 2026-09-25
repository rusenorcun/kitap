package app.kitappla.security;

import app.kitappla.api.dto.SendMessageBody;
import app.kitappla.domain.*;
import app.kitappla.repo.*;
import app.kitappla.service.*;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Güvenlik ve yetki denetimi sınırlarını (IDOR / BOLA / erişim denetimi)
 * kapsamlı biçimde doğrulayan test paketi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationAuditTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired ClaimRepository claims;
    @Autowired SwapBookRepository swapBooks;
    @Autowired SwapOfferRepository swapOffers;
    @Autowired ConversationRepository conversations;
    @Autowired ReportRepository reports;
    @Autowired PasswordEncoder encoder;

    @Autowired DonationService donationService;
    @Autowired SwapService swapService;
    @Autowired MessageService messageService;
    @Autowired ReportService reportService;

    private User userA;
    private User userB;
    private User userC;
    private User adminUser;

    private User createUser(String prefix, boolean admin) {
        User u = new User();
        u.setName(prefix + " User");
        u.setEmail(prefix.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("secret123"));
        u.setAddress("Test Adres");
        u.setAdmin(admin);
        return users.save(u);
    }

    private Book createBook(String title) {
        Book b = new Book();
        b.setTitle(title);
        return books.save(b);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @BeforeEach
    void setUp() {
        userA = createUser("Alice", false);
        userB = createUser("Bob", false);
        userC = createUser("Charlie", false);
        adminUser = createUser("Admin", true);
    }

    @Test
    void baskasininSohbetineWebErisimiEngellenir() throws Exception {
        Book book = createBook("Sohbet Testi");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(1);
        d = donations.save(d);

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(userB);
        c = claims.save(c);

        Conversation conv = messageService.open(ConversationKind.CLAIM, c.getId(), userA);
        Long convId = conv.getId();

        // Yetkili taraflar erişebilir
        mvc.perform(get("/mesajlar/" + convId).with(user(as(userA))))
                .andExpect(status().isOk());
        mvc.perform(get("/mesajlar/" + convId).with(user(as(userB))))
                .andExpect(status().isOk());

        // Yetkisiz kullanıcı (Charlie) GET /mesajlar/{id} denediğinde /mesajlar'a yönlendirilir
        mvc.perform(get("/mesajlar/" + convId).with(user(as(userC))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mesajlar"))
                .andExpect(flash().attributeExists("hata"));

        // Yetkisiz kullanıcı HTMX liste parçasını çekemez (403)
        mvc.perform(get("/mesajlar/" + convId + "/liste").with(user(as(userC))))
                .andExpect(status().isForbidden());

        // Yetkisiz kullanıcı SSE akışını dinleyemez (403)
        mvc.perform(get("/mesajlar/" + convId + "/akis").with(user(as(userC))))
                .andExpect(status().isForbidden());

        // Yetkisiz kullanıcı web üzerinden mesaj gönderemez
        mvc.perform(post("/mesajlar/" + convId).with(user(as(userC))).with(csrf())
                        .param("body", "Yetkisiz mesaj"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mesajlar"))
                .andExpect(flash().attributeExists("hata"));
    }

    @Test
    void baskasininSohbetineApiErisimiEngellenir() throws Exception {
        Book book = createBook("API Sohbet");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(1);
        d = donations.save(d);

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(userB);
        c = claims.save(c);

        Conversation conv = messageService.open(ConversationKind.CLAIM, c.getId(), userA);
        Long convId = conv.getId();

        // Charlie API ile mesajları okuyamaz (400)
        mvc.perform(get("/api/v1/conversations/" + convId + "/messages").with(user(as(userC))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bu sohbet sana ait değil."));

        // Charlie API ile mesaj gönderemez (400)
        SendMessageBody body = new SendMessageBody("Sızma denemesi");
        mvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .with(user(as(userC))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bu sohbet sana ait değil."));
    }

    @Test
    void baskasininBagisiDuzenlenemezVeyaSilinemez() throws Exception {
        Book book = createBook("Bağış Güvenliği");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(2);
        d.setStatus(DonationStatus.OPEN);
        d = donations.save(d);
        Long donationId = d.getId();

        // Bob, Alice'in bağışını kapatamaz
        mvc.perform(post("/bagis/" + donationId + "/kapat").with(user(as(userB))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bagislarim"))
                .andExpect(flash().attributeExists("hata"));

        // Bob, Alice'in bağışını silemez
        mvc.perform(post("/bagis/" + donationId + "/sil").with(user(as(userB))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bagislarim"))
                .andExpect(flash().attributeExists("hata"));

        // Bob, Alice'in bağışını takasa aktaramaz
        mvc.perform(post("/bagis/" + donationId + "/takasa-aktar").with(user(as(userB))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bagislarim"))
                .andExpect(flash().attributeExists("hata"));

        // Bob, REST API ile Alice'in bağışını kapatamaz
        mvc.perform(post("/api/v1/donations/" + donationId + "/close").with(user(as(userB))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bu bağış sana ait değil."));

        // Bağış hâlâ aktif ve açık kalmalıdır
        Donation reloaded = donations.findById(donationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DonationStatus.OPEN);
    }

    @Test
    void baskasininTakasTeklifiIslemGoremez() throws Exception {
        Book b1 = createBook("Kitap 1");
        Book b2 = createBook("Kitap 2");

        SwapBook sb1 = swapService.open(userA, b1, "A'nın kitabı");
        SwapBook sb2 = swapService.open(userB, b2, "B'nin kitabı");

        SwapOffer offer = swapService.offer(sb1.getId(), sb2.getId(), userB, "Takas teklifi");
        Long offerId = offer.getId();

        // Charlie teklif detayını görüntüleyemez
        mvc.perform(get("/takas/teklifler/" + offerId).with(user(as(userC))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("hata"));

        // Charlie takas teklifini kabul edemez
        mvc.perform(post("/takas/teklif/" + offerId + "/kabul").with(user(as(userC))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("hata"));

        // Charlie teklifi reddedemez
        mvc.perform(post("/takas/teklif/" + offerId + "/reddet").with(user(as(userC))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("hata"));

        // Charlie teklifi geri çekemez
        mvc.perform(post("/takas/teklif/" + offerId + "/geri-cek").with(user(as(userC))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("hata"));

        // Teklif hâlâ PENDING kalmalı
        SwapOffer reloaded = swapOffers.findById(offerId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    void kendiKitabinaTakasTeklifFormuEngellenir() throws Exception {
        Book book = createBook("Kendi Kitabım");
        SwapBook sb = swapService.open(userA, book, "Not");

        // Alice kendi kitabına teklif vermeye çalıştığında engellenip yönlendirilir
        mvc.perform(get("/takas/teklif/" + sb.getId()).with(user(as(userA))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas"))
                .andExpect(flash().attribute("hata", "Kendi kitabına teklif veremezsin."));

        // Bob ise Alice'in kitabına teklif formunu açabilir
        mvc.perform(get("/takas/teklif/" + sb.getId()).with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(view().name("takas-teklif"));
    }

    @Test
    void baskasininSohbetiniSikayetFormuOnDenetimleEngellenir() throws Exception {
        Book book = createBook("Gizli Sohbet");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(1);
        d = donations.save(d);

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(userB);
        c = claims.save(c);

        Conversation conv = messageService.open(ConversationKind.CLAIM, c.getId(), userA);

        // Charlie bu sohbetin tarafı değildir, şikâyet formu ekrana gelmemeli, yönlendirilmelidir
        mvc.perform(get("/sikayet/CONVERSATION/" + conv.getId()).with(user(as(userC))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panom"))
                .andExpect(flash().attributeExists("hata"));

        // Alice (taraf) ise formu açabilmelidir
        mvc.perform(get("/sikayet/CONVERSATION/" + conv.getId()).with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(view().name("sikayet"))
                .andExpect(model().attribute("refId", conv.getId()));
    }

    @Test
    void adminSikayetBildirimVeSayfaUclariCalisir() throws Exception {
        Book book = createBook("Şikâyet Edilen");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(1);
        d = donations.save(d);

        Report report = reportService.create(userB, ReportKind.DONATION, d.getId(),
                ReportReason.SPAM, "Spam içerik");
        Long reportId = report.getId();

        // Normal üye admin sayfasına erişemez (403)
        mvc.perform(get("/admin/sikayet/" + reportId).with(user(as(userA))))
                .andExpect(status().isForbidden());

        // Yönetici hem /admin/sikayetler/{id} hem de /admin/sikayet/{id} (bildirim linki) üzerinden erişebilir
        mvc.perform(get("/admin/sikayetler/" + reportId).with(user(as(adminUser))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-sikayet"));

        mvc.perform(get("/admin/sikayet/" + reportId).with(user(as(adminUser))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-sikayet"));
    }

    @Test
    void ogrenciEpostaOnayTokenYoksaHataVermez() throws Exception {
        // Token verilmediğinde 400 yerine dostane şekilde profil sayfasına yönlendirmeli
        mvc.perform(get("/profil/ogrenci/eposta/onay").with(user(as(userA))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));

        mvc.perform(post("/profil/ogrenci/eposta/onay").with(user(as(userA))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profil/ogrenci"))
                .andExpect(flash().attributeExists("hata"));
    }

    @Test
    void talepIptalindeVeGelinmedideSohbetArsivlenir() throws Exception {
        Book book = createBook("Arşivleme Testi");
        Donation d = new Donation();
        d.setDonor(userA);
        d.setBook(book);
        d.setQuantity(1);
        d = donations.save(d);

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(userB);
        c = claims.save(c);

        Conversation conv = messageService.open(ConversationKind.CLAIM, c.getId(), userA);
        assertThat(conv.isArchived()).isFalse();

        // Alıcı talebi iptal eder
        donationService.cancelClaim(c.getId(), userB);

        // Sohbet arşivlenmiş olmalı
        Conversation archived = conversations.findById(conv.getId()).orElseThrow();
        assertThat(archived.isArchived()).isTrue();

        // Arşivlenmiş sohbete yeni mesaj gönderilemez
        assertThatThrownBy(() -> messageService.send(archived.getId(), userA, "Merhaba?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arşivlendi");
    }

    @Test
    void takasGelinmediBildirimindeSohbetArsivlenir() throws Exception {
        Book b1 = createBook("Takas 1");
        Book b2 = createBook("Takas 2");

        SwapBook sb1 = swapService.open(userA, b1, "Not 1");
        SwapBook sb2 = swapService.open(userB, b2, "Not 2");

        SwapOffer offer = swapService.offer(sb1.getId(), sb2.getId(), userB, "Teklif");
        offer = swapService.accept(offer.getId(), userA);

        Conversation conv = messageService.open(ConversationKind.SWAP, offer.getId(), userA);
        assertThat(conv.isArchived()).isFalse();

        // Buluşma saati ayarla (geçmiş zaman)
        Meeting m = offer.getMeeting();
        m.setNote("Kütüphane önü");
        m.setAt(Instant.now().minus(2, ChronoUnit.HOURS));
        m.setArrangedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        swapOffers.save(offer);

        // A gelinmedi bildirimi yapar
        swapService.noShow(offer.getId(), userA);

        // Sohbet arşivlenmiş olmalı
        Conversation archived = conversations.findById(conv.getId()).orElseThrow();
        assertThat(archived.isArchived()).isTrue();

        // Yeni mesaj atılamaz
        assertThatThrownBy(() -> messageService.send(archived.getId(), userB, "Orada mısın?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arşivlendi");
    }
}
