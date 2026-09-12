package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.SwapOfferRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kampüs içi elden teslime geçerken açıkta kalan uçların testi.
 * <p>
 * Üç şey doğrulanır: (1) buluşmayı iki taraf da kendi sayfasından ayarlayabilir
 * ve o sayfaya geri döner, (2) takas hem mesajlaşmaya hem şikâyete bağlıdır,
 * (3) bağış teslim alındıktan sonra da şikâyet edilebilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EldenTeslimEksikleriTest {

    @Autowired MockMvc mvc;
    @Autowired DonationService donationService;
    @Autowired SwapService swapService;
    @Autowired BookService bookService;
    @Autowired ReportService reports;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired ClaimRepository claims;
    @Autowired SwapOfferRepository offers;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean ogrenci) {
        User u = new User();
        u.setName("Elden " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        if (ogrenci) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    private Book kitap(String ad) {
        Book b = new Book();
        b.setTitle(ad + " " + UUID.randomUUID());
        return books.save(b);
    }

    private Claim teslimat(User donor, User alici) {
        Donation d = donationService.create(donor, kitap("Bağış"), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        return donationService.claim(d.getId(), alici);
    }

    private static String yerelZaman(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneId.systemDefault()).withNano(0).toString();
    }

    // ---------- Buluşma: iki taraf da ayarlar, geldiği sayfaya döner ----------

    @Test
    void bagisciBulusmayiBagislarimdanAyarlarVeOrayaDoner() throws Exception {
        User donor = mk("bagisci", false);
        User alici = mk("alici", true);
        Claim c = teslimat(donor, alici);

        mvc.perform(post("/bulusma/bagis/" + c.getId()).with(user(as(donor))).with(csrf())
                        .param("note", "Kütüphane girişi")
                        .param("at", yerelZaman(Instant.now().plusSeconds(7200)))
                        .param("geri", "/bagislarim"))
                .andExpect(redirectedUrl("/bagislarim"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.ARRANGED);
    }

    @Test
    void aliciBulusmayiAldiklarimdanAyarlarVeOrayaDoner() throws Exception {
        User donor = mk("bagisci2", false);
        User alici = mk("alici2", true);
        Claim c = teslimat(donor, alici);

        mvc.perform(post("/bulusma/bagis/" + c.getId()).with(user(as(alici))).with(csrf())
                        .param("note", "Kantin")
                        .param("at", yerelZaman(Instant.now().plusSeconds(7200)))
                        .param("geri", "/aldiklarim"))
                .andExpect(redirectedUrl("/aldiklarim"));
    }

    @Test
    void taninmayanGeriAdresiVarsayilanaDuser() throws Exception {
        User donor = mk("bagisci3", false);
        User alici = mk("alici3", true);
        Claim c = teslimat(donor, alici);

        // Açık yönlendirme denemesi: dış adrese gidilmemeli
        mvc.perform(post("/bulusma/bagis/" + c.getId()).with(user(as(donor))).with(csrf())
                        .param("note", "Kapı önü")
                        .param("at", yerelZaman(Instant.now().plusSeconds(7200)))
                        .param("geri", "https://kotu.example/phishing"))
                .andExpect(redirectedUrl("/aldiklarim"));
    }

    @Test
    void bagisciSayfasindaGelmediBildirimiVar() throws Exception {
        User donor = mk("bagisci4", false);
        User alici = mk("alici4", true);
        Claim c = teslimat(donor, alici);
        // Buluşma ileri bir saate kurulur, sonra saati geçmişe çekilir
        donationService.arrange(c.getId(), donor,
                new MeetingRequest(null, "Kütüphane", Instant.now().plusSeconds(60)));
        Claim kayit = claims.findById(c.getId()).orElseThrow();
        kayit.getMeeting().setAt(Instant.now().minusSeconds(3600));
        claims.save(kayit);

        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/gelmedi/bagis/" + c.getId())));

        mvc.perform(post("/gelmedi/bagis/" + c.getId()).with(user(as(donor))).with(csrf())
                        .param("geri", "/bagislarim"))
                .andExpect(redirectedUrl("/bagislarim"));

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.NO_SHOW);
    }

    // ---------- Bağış: teslim alındıktan sonra da şikâyet ----------

    @Test
    void teslimAlindiktanSonraSikayetEdilebilir() throws Exception {
        User donor = mk("bagisci5", false);
        User alici = mk("alici5", true);
        Claim c = teslimat(donor, alici);
        donationService.arrange(c.getId(), donor,
                new MeetingRequest(null, "Kütüphane", Instant.now().plusSeconds(3600)));
        donationService.deliver(c.getId(), alici);
        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.DELIVERED);

        // Teslim sonrası sayfa hâlâ şikâyet bağlantısını göstermeli
        mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/sikayet/claim/" + c.getId())));

        mvc.perform(post("/sikayet/claim/" + c.getId()).with(user(as(alici))).with(csrf())
                        .param("reason", "HASARLI")
                        .param("note", "Teslim aldıktan sonra sayfaların eksik olduğunu gördüm")
                        .param("geri", "/aldiklarim"))
                .andExpect(redirectedUrl("/aldiklarim"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(reports.mine(alici))
                .anyMatch(r -> r.getKind() == ReportKind.CLAIM && r.getRefId().equals(c.getId()));
    }

    @Test
    void bagisciDaTeslimSonrasiSikayetEdebilir() throws Exception {
        User donor = mk("bagisci6", false);
        User alici = mk("alici6", true);
        Claim c = teslimat(donor, alici);
        donationService.arrange(c.getId(), alici,
                new MeetingRequest(null, "Kantin", Instant.now().plusSeconds(3600)));
        donationService.deliver(c.getId(), alici);

        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/sikayet/claim/" + c.getId())));

        mvc.perform(post("/sikayet/claim/" + c.getId()).with(user(as(donor))).with(csrf())
                        .param("reason", "GELMEDI").param("geri", "/bagislarim"))
                .andExpect(redirectedUrl("/bagislarim"))
                .andExpect(flash().attributeExists("basari"));
    }

    // ---------- Takas: mesajlaşma ve şikâyet ----------

    private SwapOffer teklif(User veren, User alan) {
        SwapBook benim = swapService.open(veren, kitap("Takas A"), "İlgilenirim");
        SwapBook onun = swapService.open(alan, kitap("Takas B"), "Değişirim");
        return swapService.offer(onun.getId(), benim.getId(), veren, "Merhaba");
    }

    @Test
    void takasIlanıSikayetEdilebilirVeListedeBaglantisiVar() throws Exception {
        User sahibi = mk("takas-sahip", false);
        User bakan = mk("takas-bakan", false);
        SwapBook ilan = swapService.open(sahibi, kitap("Takas İlanı"), "Polisiye olsun");
        // Teklif verebilmek için bakanın da bir kitabı olsun (keşif sayfası bunu bekler)
        swapService.open(bakan, kitap("Bakanın Kitabı"), null);

        mvc.perform(get("/takas").with(user(as(bakan))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/sikayet/swap_book/" + ilan.getId())));

        mvc.perform(post("/sikayet/swap_book/" + ilan.getId()).with(user(as(bakan))).with(csrf())
                        .param("reason", "SAHTE").param("note", "İlan yanıltıcı")
                        .param("geri", "/takas"))
                .andExpect(redirectedUrl("/takas"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(reports.mine(bakan))
                .anyMatch(r -> r.getKind() == ReportKind.SWAP_BOOK && r.getRefId().equals(ilan.getId()));
    }

    @Test
    void takasSurecindeMesajlasmaAcilir() throws Exception {
        User veren = mk("takas-veren", false);
        User alan = mk("takas-alan", false);
        SwapOffer o = teklif(veren, alan);

        mvc.perform(get("/mesajlar/ac/swap/" + o.getId()).with(user(as(alan))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/mesajlar/*"));

        // Takaslarım sayfası hem mesaj hem şikâyet bağlantısını göstermeli
        mvc.perform(get("/takas/takaslarim").with(user(as(alan))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/mesajlar/ac/swap/" + o.getId())))
                .andExpect(content().string(containsString("/sikayet/swap_offer/" + o.getId())));
    }

    @Test
    void tamamlanmisTakasSonrasiSikayetEdilebilir() throws Exception {
        User veren = mk("takas-veren2", false);
        User alan = mk("takas-alan2", false);
        SwapOffer o = teklif(veren, alan);
        swapService.accept(o.getId(), alan);
        swapService.arrange(o.getId(), alan,
                new MeetingRequest(null, "Kütüphane", Instant.now().plusSeconds(3600)));
        swapService.ship(o.getId(), veren);
        swapService.ship(o.getId(), alan);
        assertThat(offers.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.COMPLETED);

        mvc.perform(get("/takas/takaslarim").with(user(as(veren))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/sikayet/swap_offer/" + o.getId())));

        mvc.perform(post("/sikayet/swap_offer/" + o.getId()).with(user(as(veren))).with(csrf())
                        .param("reason", "HASARLI").param("note", "Kitap ilandakinden farklıydı")
                        .param("geri", "/takas/takaslarim"))
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("basari"));
    }

    @Test
    void takasTeslimOnayiGeldigiSayfayaDoner() throws Exception {
        User veren = mk("takas-veren3", false);
        User alan = mk("takas-alan3", false);
        SwapOffer o = teklif(veren, alan);
        swapService.accept(o.getId(), alan);
        swapService.arrange(o.getId(), alan,
                new MeetingRequest(null, "Kütüphane", Instant.now().plusSeconds(3600)));

        mvc.perform(post("/takas/teklif/" + o.getId() + "/kargola").with(user(as(veren))).with(csrf())
                        .param("geri", "/takas/teklifler/" + o.getId()))
                .andExpect(redirectedUrl("/takas/teklifler/" + o.getId()));
    }

    @Test
    void sikayetFormuDisAdreseYonlendirmez() throws Exception {
        User sahibi = mk("acik-sahip", false);
        User bakan = mk("acik-bakan", false);
        SwapBook ilan = swapService.open(sahibi, kitap("Açık Yönlendirme"), null);

        // Form, dış adresi modele koymaz
        mvc.perform(get("/sikayet/swap_book/" + ilan.getId()).with(user(as(bakan)))
                        .param("geri", "https://kotu.example/phishing"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("kotu.example"))));

        // Gönderim de dış adrese yönlendirmez
        mvc.perform(post("/sikayet/swap_book/" + ilan.getId()).with(user(as(bakan))).with(csrf())
                        .param("reason", "SPAM")
                        .param("geri", "//kotu.example/phishing"))
                .andExpect(redirectedUrl("/panom"));
    }

    // ---------- Şablon tuzağı: th:if + th:replace ----------

    /**
     * Thymeleaf'te th:replace, th:if'ten önce işlenir; ikisi aynı etikette
     * olursa koşul hiç değerlendirilmez. Teslim edilmiş bir kayıtta buluşma
     * kartının iki kez basılması ve kapalı olması gereken formların görünmesi
     * bu yüzdendi.
     */
    @Test
    void teslimSonrasiBulusmaKartiTekKezVeFormsuzGorunur() throws Exception {
        User donor = mk("bagisci7", false);
        User alici = mk("alici7", true);
        Claim c = teslimat(donor, alici);
        donationService.arrange(c.getId(), donor,
                new MeetingRequest(null, "Yemekhane önü", Instant.now().plusSeconds(3600)));
        donationService.deliver(c.getId(), alici);

        String sayfa = mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(sayfa.split("Yemekhane önü", -1).length - 1)
                .as("teslim edilmiş kayıtta buluşma kartı yalnızca bir kez basılmalı")
                .isEqualTo(1);
        assertThat(sayfa).doesNotContain("/bulusma/bagis/" + c.getId());
        assertThat(sayfa).doesNotContain("/gelmedi/bagis/" + c.getId());

        String bagisciSayfasi = mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(bagisciSayfasi).doesNotContain("/bulusma/bagis/" + c.getId());
    }

    // ---------- Şikâyetlerim sayfası ----------

    @Test
    void sikayetlerimSayfasiKendiKayitlariniGosterir() throws Exception {
        User sahibi = mk("liste-sahip", false);
        User bakan = mk("liste-bakan", false);
        SwapBook ilan = swapService.open(sahibi, kitap("Liste İlanı"), null);
        reports.create(bakan, ReportKind.SWAP_BOOK, ilan.getId(), ReportReason.SPAM, "Reklam gibi");

        mvc.perform(get("/sikayetlerim").with(user(as(bakan))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reklam gibi")))
                .andExpect(content().string(containsString("Takas ilanı")));

        // Başkasının şikâyeti burada görünmez
        mvc.perform(get("/sikayetlerim").with(user(as(sahibi))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Reklam gibi"))));
    }
}
