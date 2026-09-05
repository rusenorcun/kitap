package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Şikâyet kaydı, erişim denetimi ve moderasyon gizliliği. */
@SpringBootTest
@ActiveProfiles("test")
class ReportServiceTest {

    @Autowired ReportService reports;
    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired NotificationService notifications;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean ogrenci) {
        User u = new User();
        u.setName("Sikayet " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        if (ogrenci) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private Book book() {
        Book b = new Book();
        b.setTitle("Kitap " + UUID.randomUUID());
        return books.save(b);
    }

    private Conversation sohbet(User donor, User alici) {
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        return messages.open(ConversationKind.CLAIM, c.getId(), alici);
    }

    @Test
    void sohbetSikayetEdilirVeKarsiTarafKaydedilir() {
        User donor = mk("bagisci", false);
        User alici = mk("alici", true);
        Conversation s = sohbet(donor, alici);

        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.TACIZ, "kaba konuştu");

        assertThat(r.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(r.getReportedUser().getId()).isEqualTo(donor.getId());
        assertThat(r.getNote()).isEqualTo("kaba konuştu");
        assertThat(reports.open()).anyMatch(x -> x.getId().equals(r.getId()));
    }

    @Test
    void tarafOlmayanSohbetiSikayetEdemez() {
        User donor = mk("s1", false);
        User alici = mk("s2", true);
        User yabanci = mk("yabanci", false);
        Conversation s = sohbet(donor, alici);

        assertThatThrownBy(() -> reports.create(yabanci, ReportKind.CONVERSATION, s.getId(),
                ReportReason.SPAM, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sana ait değil");
    }

    @Test
    void kendiIceriginiSikayetEdemez() {
        User donor = mk("kendi", false);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);

        assertThatThrownBy(() -> reports.create(donor, ReportKind.DONATION, d.getId(),
                ReportReason.SPAM, null))
                .hasMessageContaining("Kendi içeriğini");
    }

    @Test
    void ayniSeyIkinciKezSikayetEdilemez() {
        User donor = mk("tek1", false);
        User alici = mk("tek2", true);
        Conversation s = sohbet(donor, alici);
        reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.SPAM, null);

        assertThatThrownBy(() -> reports.create(alici, ReportKind.CONVERSATION, s.getId(),
                ReportReason.TACIZ, null))
                .hasMessageContaining("zaten şikâyet ettin");
    }

    @Test
    void gerekcesizSikayetReddedilir() {
        User donor = mk("g1", false);
        User alici = mk("g2", true);
        Conversation s = sohbet(donor, alici);

        assertThatThrownBy(() -> reports.create(alici, ReportKind.CONVERSATION, s.getId(), null, null))
                .hasMessageContaining("gerekçesi seçilmeli");
    }

    @Test
    void yoneticiyeBildirimGider() {
        User yonetici = mk("yonetici", false);
        yonetici.setAdmin(true);
        users.save(yonetici);

        User donor = mk("b1", false);
        User alici = mk("b2", true);
        Conversation s = sohbet(donor, alici);
        reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.TACIZ, null);

        assertThat(notifications.latest(yonetici))
                .anyMatch(n -> n.getType().equals("sikayet") && n.getMessage().contains("Taciz"));
    }

    // ---------- Moderasyon gizliliği ----------

    @Test
    void sikayetsizSohbetYONETIME_DE_KAPALI() {
        User donor = mk("gizli1", false);
        User alici = mk("gizli2", true);
        Conversation s = sohbet(donor, alici);
        messages.send(s.getId(), alici, "özel mesaj");

        // Şikâyet yokken yönetici de okuyamaz
        assertThatThrownBy(() -> messages.requireForModeration(s.getId(), reports))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yönetime kapalıdır");
    }

    @Test
    void sikayetEdilinceYoneticiOkuyabilirKapaninca_ERISIM_KALKAR() {
        User yonetici = mk("mod", false);
        yonetici.setAdmin(true);
        users.save(yonetici);

        User donor = mk("mod1", false);
        User alici = mk("mod2", true);
        Conversation s = sohbet(donor, alici);
        messages.send(s.getId(), alici, "incelenecek mesaj");

        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.TACIZ, null);

        // Açık şikâyet varken okunabilir
        Conversation acik = messages.requireForModeration(s.getId(), reports);
        assertThat(messages.messagesOf(acik))
                .anyMatch(m -> m.getBody().equals("incelenecek mesaj"));

        // Şikâyet kapatılınca erişim geri kapanır
        reports.resolve(r.getId(), yonetici, true, "uyarı verildi");
        assertThatThrownBy(() -> messages.requireForModeration(s.getId(), reports))
                .hasMessageContaining("yönetime kapalıdır");
    }

    @Test
    void sonuclandirmaSikayetEdeneBildirilir() {
        User yonetici = mk("kapatan", false);
        yonetici.setAdmin(true);
        users.save(yonetici);

        User donor = mk("k1", false);
        User alici = mk("k2", true);
        Conversation s = sohbet(donor, alici);
        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.SPAM, null);

        reports.resolve(r.getId(), yonetici, false, "kural dışı bir şey yok");

        assertThat(notifications.latest(alici))
                .anyMatch(n -> n.getType().equals("sikayet_sonuc")
                        && n.getMessage().contains("kural dışı bir şey yok"));
        assertThat(reports.find(r.getId()).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.DISMISSED);
    }

    @Test
    void kapaliSikayetTekrarSonuclandirilamaz() {
        User yonetici = mk("iki-kez", false);
        yonetici.setAdmin(true);
        users.save(yonetici);

        User donor = mk("i1", false);
        User alici = mk("i2", true);
        Conversation s = sohbet(donor, alici);
        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.SPAM, null);
        reports.resolve(r.getId(), yonetici, true, null);

        assertThatThrownBy(() -> reports.resolve(r.getId(), yonetici, false, null))
                .hasMessageContaining("zaten sonuçlandırılmış");
    }

    @Autowired RequestService requestService;
    @Autowired SwapService swapService;

    @Test
    void bagisVeUyeDeSikayetEdilebilir() {
        User donor = mk("ilan", false);
        User sikayetci = mk("bildiren", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);

        Report r1 = reports.create(sikayetci, ReportKind.DONATION, d.getId(), ReportReason.SAHTE, null);
        assertThat(r1.getReportedUser().getId()).isEqualTo(donor.getId());

        Report r2 = reports.create(sikayetci, ReportKind.USER, donor.getId(), ReportReason.TICARET, null);
        assertThat(r2.getReportedUser().getId()).isEqualTo(donor.getId());
    }

    @Test
    void claimTeslimatSonrasiSikayetEdilebilir() {
        User donor = mk("cdonor", false);
        User student = mk("cstudent", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), student);

        // Alıcı öğrenci teslimatı şikâyet eder
        Report r = reports.create(student, ReportKind.CLAIM, c.getId(), ReportReason.HASARLI, "Kitabın sayfaları eksik");
        assertThat(r.getReportedUser().getId()).isEqualTo(donor.getId());
        assertThat(r.getReason()).isEqualTo(ReportReason.HASARLI);

        // Bağışçı da şikâyet edebilir
        User donor2 = mk("cdonor2", false);
        User student2 = mk("cstudent2", true);
        Donation d2 = donationService.create(donor2, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c2 = donationService.claim(d2.getId(), student2);
        Report r2 = reports.create(donor2, ReportKind.CLAIM, c2.getId(), ReportReason.TESLIMAT_SORUNU, "Gelmeyi reddetti");
        assertThat(r2.getReportedUser().getId()).isEqualTo(student2.getId());

        // Yabancı şikâyet edemez
        User yabanci = mk("cyabanci", false);
        assertThatThrownBy(() -> reports.create(yabanci, ReportKind.CLAIM, c.getId(), ReportReason.HASARLI, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sana ait değil");
    }

    @Test
    void karsilananIstekTeslimatiSikayetEdilebilir() {
        User student = mk("rstudent", true);
        User fulfiller = mk("rfulfiller", false);
        BookRequest req = requestService.create(student, book(), "İhtiyaç");
        requestService.fulfill(req.getId(), fulfiller, DonationSource.OWN);

        // İsteyen öğrenci karşılayanı şikâyet eder
        Report r = reports.create(student, ReportKind.REQUEST, req.getId(), ReportReason.HASARLI, "Yanlış baskı geldi");
        assertThat(r.getReportedUser().getId()).isEqualTo(fulfiller.getId());
    }

    @Test
    void takasSureciSikayetEdilebilir() {
        User ali = mk("tali", false);
        User veli = mk("tveli", false);
        SwapBook b1 = swapService.open(ali, book(), "Takaslık");
        SwapBook b2 = swapService.open(veli, book(), "Takaslık");
        SwapOffer offer = swapService.offer(b2.getId(), b1.getId(), ali, "Takas edelim");

        // Teklifin tarafı şikâyet edebilir
        Report r = reports.create(ali, ReportKind.SWAP_OFFER, offer.getId(), ReportReason.TESLIMAT_SORUNU, "İletişim koptu");
        assertThat(r.getReportedUser().getId()).isEqualTo(veli.getId());

        // Yabancı şikâyet edemez
        User yabanci = mk("tyabanci", false);
        assertThatThrownBy(() -> reports.create(yabanci, ReportKind.SWAP_OFFER, offer.getId(), ReportReason.SPAM, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sana ait değil");
    }
}
