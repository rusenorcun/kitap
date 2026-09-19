package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Üye askıya alınınca karşı tarafı olan süreçleri (talep, istek, takas) iptal edilir;
 * karşı tarafın hakkı (kota, bağış adedi, takas ilanı) iade edilir ve bildirim gider.
 */
@SpringBootTest
@ActiveProfiles("test")
class AskiyaAlmaSurecIptaliTest {

    private static final String IADE = "askıya alındığı için buluşma iptal edildi ve hakkınız iade edildi";

    @Autowired AdminService admin;
    @Autowired DonationService donationService;
    @Autowired RequestService requestService;
    @Autowired SwapService swapService;
    @Autowired QuotaService quota;
    @Autowired MessageService messages;
    @Autowired BookService bookService;
    @Autowired UserRepository users;
    @Autowired ClaimRepository claims;
    @Autowired DonationRepository donations;
    @Autowired BookRequestRepository requests;
    @Autowired SwapOfferRepository offers;
    @Autowired SwapBookRepository swapBooks;
    @Autowired NotificationRepository notifications;
    @Autowired ConversationRepository conversations;

    private User mk(String tag) {
        User u = new User();
        u.setName("Aski " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress("Erzurum");
        // Yeni bağış 48 saat öğrencilere öncelikli; alıcılar onaylı öğrenci olmalı
        u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        return users.save(u);
    }

    private User yonetici() {
        User a = mk("yonetici");
        a.setAdmin(true);
        return users.save(a);
    }

    private Book book() {
        return bookService.findOrCreate("Askı Kitabı " + UUID.randomUUID(), "Yazar", null, null, null, null);
    }

    private MeetingRequest bulusma() {
        return new MeetingRequest(null, "Kütüphane girişi", Instant.now().plusSeconds(3600));
    }

    private String sonBildirim(User u) {
        return notifications.findTop50ByUserOrderByCreatedAtDesc(u).get(0).getMessage();
    }

    // ---------- Bağış talepleri ----------

    @Test
    void bagisciAskiyaAlinincaBulusmaIptalVeAlicininKotaHakkiIadeEdilir() {
        User bagisci = mk("bagisci");
        User alici = mk("alici");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), alici, bulusma());
        Conversation sohbet = messages.open(ConversationKind.CLAIM, c.getId(), alici);
        long kullanilan = quota.quotaFor(alici).weeklyUsed();

        admin.setBlocked(yonetici(), bagisci.getId(), true);

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.CANCELLED);
        assertThat(quota.quotaFor(alici).weeklyUsed()).isEqualTo(kullanilan - 1);
        assertThat(sonBildirim(alici)).contains(IADE);
        assertThat(conversations.findById(sohbet.getId()).orElseThrow().isArchived()).isTrue();
        // İptal edilen kayıt buluşma/teslim/sohbetle diriltilemez
        assertThatThrownBy(() -> donationService.arrange(c.getId(), alici, bulusma()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> donationService.deliver(c.getId(), alici))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messages.open(ConversationKind.CLAIM, c.getId(), alici))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aliciAskiyaAlinincaBagisAdediBagisciyaGeriDoner() {
        User bagisci = mk("bagisci2");
        User alici = mk("alici2");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);

        admin.setBlocked(yonetici(), alici.getId(), true);

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.CANCELLED);
        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.OPEN);
        assertThat(donationService.view(d.getId()).orElseThrow().remaining()).isEqualTo(1);
        assertThat(sonBildirim(bagisci)).contains("askıya alındığı için").contains("hakkınız iade edildi");
    }

    @Test
    void teslimEdilmisTalepAskidanEtkilenmez() {
        User bagisci = mk("bagisci3");
        User alici = mk("alici3");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.arrange(c.getId(), bagisci, bulusma());
        donationService.deliver(c.getId(), alici);

        admin.setBlocked(yonetici(), bagisci.getId(), true);

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.DELIVERED);
    }

    // ---------- İstekler ----------

    @Test
    void karsilayanAskiyaAlinincaIstekYenidenAcilirVeIsteyeninHakkiIadeEdilir() {
        User isteyen = mk("isteyen");
        User karsilayan = mk("karsilayan");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);
        requestService.arrange(r.getId(), isteyen, bulusma());
        long kullanilan = quota.quotaFor(isteyen).weeklyUsed();

        admin.setBlocked(yonetici(), karsilayan.getId(), true);

        BookRequest sonra = requests.findById(r.getId()).orElseThrow();
        assertThat(sonra.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(sonra.getFulfilledBy()).isNull();
        assertThat(sonra.getMeeting().isArranged()).isFalse();
        assertThat(quota.quotaFor(isteyen).weeklyUsed()).isEqualTo(kullanilan - 1);
        assertThat(sonBildirim(isteyen)).contains(IADE);
    }

    @Test
    void isteyenAskiyaAlinincaKarsilananIstekIptalEdilir() {
        User isteyen = mk("isteyen2");
        User karsilayan = mk("karsilayan2");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        admin.setBlocked(yonetici(), isteyen.getId(), true);

        assertThat(requests.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(sonBildirim(karsilayan)).contains("askıya alındığı için");
        assertThatThrownBy(() -> requestService.arrange(r.getId(), karsilayan, bulusma()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Takas ----------

    @Test
    void kabulEdilmisTakastaKarsiTarafinKitabiYenidenTakasaAcilir() {
        User ali = mk("ali");
        User veli = mk("veli");
        SwapBook aliKitap = swapService.open(ali, book(), null);
        SwapBook veliKitap = swapService.open(veli, book(), null);
        SwapOffer o = swapService.offer(aliKitap.getId(), veliKitap.getId(), veli, "Takas?");
        swapService.accept(o.getId(), ali);
        swapService.arrange(o.getId(), ali, bulusma());

        admin.setBlocked(yonetici(), veli.getId(), true);

        assertThat(offers.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertThat(swapBooks.findById(aliKitap.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.OPEN);
        assertThat(swapBooks.findById(veliKitap.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);
        assertThat(sonBildirim(ali)).contains(IADE);
    }

    @Test
    void askidakiUyeyeGelenBekleyenTeklifIptalEdilir() {
        User ali = mk("ali2");
        User veli = mk("veli2");
        SwapBook aliKitap = swapService.open(ali, book(), null);
        SwapBook veliKitap = swapService.open(veli, book(), null);
        SwapOffer o = swapService.offer(aliKitap.getId(), veliKitap.getId(), veli, "Takas?");

        admin.setBlocked(yonetici(), ali.getId(), true);

        assertThat(offers.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertThat(swapBooks.findById(veliKitap.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.OPEN);
        assertThat(sonBildirim(veli)).contains("askıya alındığı için");
    }

    @Test
    void askiKaldirilincaIptalEdilenSurecGeriGelmez() {
        User bagisci = mk("bagisci4");
        User alici = mk("alici4");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        User yonetici = yonetici();

        admin.setBlocked(yonetici, bagisci.getId(), true);
        admin.setBlocked(yonetici, bagisci.getId(), false);

        assertThat(claims.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.CANCELLED);
    }
}
