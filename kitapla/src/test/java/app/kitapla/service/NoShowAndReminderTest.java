package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gelinmedi bildirimi, kota etkisi ve buluşma hatırlatması. */
@SpringBootTest
@ActiveProfiles("test")
class NoShowAndReminderTest {

    @Autowired DonationService donationService;
    @Autowired RequestService requestService;
    @Autowired SwapService swapService;
    @Autowired ReminderService reminders;
    @Autowired QuotaService quotas;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired ClaimRepository claims;
    @Autowired DonationRepository donations;
    @Autowired BookRequestRepository requests;
    @Autowired SwapOfferRepository offers;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean ogrenci) {
        User u = new User();
        u.setName("Gelmedi " + tag);
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

    /** Buluşması geçmişte olan bir talep kurar. */
    private Claim gecmisBulusma(User donor, User alici, int adet) {
        Donation d = donationService.create(donor, book(), adet, TargetLevel.HEPSI,
                DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), alici, new MeetingRequest(
                null, "Kütüphane", Instant.now().plusSeconds(60)));
        // Buluşma saatini geçmişe çek
        Claim k = claims.findByIdWithDetails(c.getId()).orElseThrow();
        k.getMeeting().setAt(Instant.now().minus(2, ChronoUnit.HOURS));
        claims.save(k);
        return k;
    }

    // ---------- Gelinmedi ----------

    @Test
    void bulusmaSaatiGelmedenBildirilemez() {
        User donor = mk("erken1", false);
        User alici = mk("erken2", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), alici, new MeetingRequest(
                null, "Kütüphane", Instant.now().plus(2, ChronoUnit.HOURS)));

        assertThatThrownBy(() -> donationService.noShow(c.getId(), donor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saati daha gelmedi");
    }

    @Test
    void bulusmaAyarlanmadanBildirilemez() {
        User donor = mk("ayarsiz1", false);
        User alici = mk("ayarsiz2", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);

        assertThatThrownBy(() -> donationService.noShow(c.getId(), donor))
                .hasMessageContaining("buluşma ayarlanmış olmalı");
    }

    @Test
    void ucuncuKisiBildiremez() {
        User donor = mk("u1", false);
        User alici = mk("u2", true);
        User yabanci = mk("u3", false);
        Claim c = gecmisBulusma(donor, alici, 1);

        assertThatThrownBy(() -> donationService.noShow(c.getId(), yabanci))
                .hasMessageContaining("sana ait değil");
    }

    @Test
    void KITAP_HAVUZA_DONER_ama_KOTA_HAKKI_YANAR() {
        User donor = mk("kota1", false);
        User alici = mk("kota2", true);

        long oncekiKota = quotas.quotaFor(alici).weeklyUsed();
        Claim c = gecmisBulusma(donor, alici, 1);
        assertThat(quotas.quotaFor(alici).weeklyUsed()).isEqualTo(oncekiKota + 1);

        // Bağış tek adetlikti; talep varken kalan 0 olmalı
        Long donationId = c.getDonation().getId();
        assertThat(donationService.view(donationId).orElseThrow().getRemaining()).isZero();

        donationService.noShow(c.getId(), donor);

        // Kitap yeniden havuzda
        assertThat(donationService.view(donationId).orElseThrow().getRemaining()).isEqualTo(1);
        // Ama kota hakkı yanmış durumda — gelmemek kotayı sıfırlamanın yolu olmamalı
        assertThat(quotas.quotaFor(alici).weeklyUsed())
                .as("gelmeyen kişinin kota hakkı geri verilmemeli")
                .isEqualTo(oncekiKota + 1);
    }

    @Test
    void herIkiTarafaDaBildirimGiderVeSayacArtar() {
        User donor = mk("bildirim1", false);
        User alici = mk("bildirim2", true);
        Claim c = gecmisBulusma(donor, alici, 1);

        donationService.noShow(c.getId(), donor);

        assertThat(users.findById(alici.getId()).orElseThrow().getNoShowCount()).isEqualTo(1);
        assertThat(notifications.latest(alici)).anyMatch(n -> n.getType().equals("gelmedi"));
        assertThat(notifications.latest(donor)).anyMatch(n -> n.getType().equals("gelmedi_kayit"));
    }

    @Test
    void ayniKayitIkinciKezBildirilemez() {
        User donor = mk("iki1", false);
        User alici = mk("iki2", true);
        Claim c = gecmisBulusma(donor, alici, 1);
        donationService.noShow(c.getId(), donor);

        assertThatThrownBy(() -> donationService.noShow(c.getId(), donor))
                .hasMessageContaining("zaten gelinmedi");
    }

    @Test
    void istekteKarsilayanGelmezseIstekYENIDEN_ACILIR() {
        User isteyen = mk("istek1", true);
        User karsilayan = mk("istek2", false);
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);
        requestService.arrange(r.getId(), isteyen, new MeetingRequest(
                null, "Kantin", Instant.now().plusSeconds(60)));
        var kayit = requestService.view(r.getId()).orElseThrow();
        kayit.getMeeting().setAt(Instant.now().minus(2, ChronoUnit.HOURS));
        requests.save(kayit);

        requestService.noShow(r.getId(), isteyen);

        var sonra = requestService.view(r.getId()).orElseThrow();
        assertThat(sonra.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(sonra.getFulfilledBy()).isNull();
        assertThat(users.findById(karsilayan.getId()).orElseThrow().getNoShowCount()).isEqualTo(1);
    }

    @Test
    void takastaGelinmezseKitaplarYenidenAcilir() {
        User ali = mk("takas1", false);
        User veli = mk("takas2", false);
        SwapBook a = swapService.open(ali, book(), null);
        SwapBook v = swapService.open(veli, book(), null);
        SwapOffer o = swapService.offer(a.getId(), v.getId(), veli, null);
        swapService.accept(o.getId(), ali);
        swapService.arrange(o.getId(), ali, new MeetingRequest(
                null, "Yemekhane", Instant.now().plusSeconds(60)));
        var teklif = swapService.viewBook(a.getId());   // kitaplar kapanmış olmalı
        assertThat(teklif.orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);

        // Buluşmayı geçmişe al
        var o2 = swapService.incoming(ali).stream().filter(x -> x.getId().equals(o.getId()))
                .findFirst().orElseThrow();
        o2.getMeeting().setAt(Instant.now().minus(2, ChronoUnit.HOURS));
        offers.save(o2);

        swapService.noShow(o.getId(), ali);

        assertThat(swapService.viewBook(a.getId()).orElseThrow().getStatus())
                .isEqualTo(SwapBookStatus.OPEN);
        assertThat(users.findById(veli.getId()).orElseThrow().getNoShowCount()).isEqualTo(1);
    }

    // ---------- Hatırlatma ----------

    @Test
    void yaklasanBulusmaIcinIKI_TARAFA_hatirlatmaGider() {
        User donor = mk("hatirla1", false);
        User alici = mk("hatirla2", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), alici, new MeetingRequest(
                null, "Kütüphane girişi", Instant.now().plus(90, ChronoUnit.MINUTES)));

        int gonderilen = reminders.sendDue();
        assertThat(gonderilen).isGreaterThanOrEqualTo(2);

        assertThat(notifications.latest(alici))
                .anyMatch(n -> n.getType().equals("bulusma_hatirlatma")
                        && n.getMessage().contains("Kütüphane girişi"));
        assertThat(notifications.latest(donor))
                .anyMatch(n -> n.getType().equals("bulusma_hatirlatma"));
    }

    @Test
    void ayniBulusmaIKINCI_KEZ_hatirlatilmaz() {
        User donor = mk("tekrar1", false);
        User alici = mk("tekrar2", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), alici, new MeetingRequest(
                null, "Kantin", Instant.now().plus(60, ChronoUnit.MINUTES)));

        reminders.sendDue();
        long oncekiSayi = notifications.latest(alici).stream()
                .filter(n -> n.getType().equals("bulusma_hatirlatma")).count();

        reminders.sendDue();   // ikinci tur
        long sonrakiSayi = notifications.latest(alici).stream()
                .filter(n -> n.getType().equals("bulusma_hatirlatma")).count();

        assertThat(sonrakiSayi).isEqualTo(oncekiSayi);
    }

    @Test
    void uzaktakiBulusmaIcinHenuzHatirlatilmaz() {
        User donor = mk("uzak1", false);
        User alici = mk("uzak2", true);
        Donation d = donationService.create(donor, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        // Eşik 3 saat; 2 gün sonrası için hatırlatma çıkmamalı
        donationService.arrange(c.getId(), alici, new MeetingRequest(
                null, "İleri tarih", Instant.now().plus(2, ChronoUnit.DAYS)));

        reminders.sendDue();

        assertThat(notifications.latest(alici))
                .noneMatch(n -> n.getType().equals("bulusma_hatirlatma")
                        && n.getMessage().contains("İleri tarih"));
    }
}
