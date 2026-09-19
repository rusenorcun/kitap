package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Askıdaki üyenin ilanları ve yönetimin kaldırdığı ilanlar: keşifte görünmez,
 * işlem almaz ve sahibi ya da yan akışlar (iptal, gelinmedi) tarafından yeniden açılmaz.
 */
@SpringBootTest
@ActiveProfiles("test")
class ModerasyonVeAskiTest {

    @Autowired AdminService admin;
    @Autowired DonationService donationService;
    @Autowired SwapService swapService;
    @Autowired RequestService requestService;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired ClaimRepository claims;
    @Autowired SwapBookRepository swapBooks;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag) {
        User u = new User();
        u.setName("Denetim " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        return users.save(u);
    }

    private User ogrenci(String tag) {
        User u = mk(tag);
        u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        return users.save(u);
    }

    private Book book() {
        Book b = new Book();
        b.setTitle("Denetim Kitabı " + UUID.randomUUID());
        b.setAuthor("Yazar");
        return books.save(b);
    }

    private User askiya(User u) {
        u.setBlocked(true);
        return users.save(u);
    }

    // ---------- Askıdaki üye ----------

    @Test
    void askidakiBagiscininBagisiKesifteGorunmezVeAlinamaz() {
        User bagisci = mk("aski-bagisci");
        Book kitap = book();
        Donation d = donationService.create(bagisci, kitap, 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        askiya(bagisci);

        assertThat(donationService.openDonations(new DonationService.Filter(null, kitap.getTitle(), false)))
                .extracting(DonationView::getId).doesNotContain(d.getId());
        assertThatThrownBy(() -> donationService.claim(d.getId(), ogrenci("aski-alici")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void askidakiUyeninTakasIlaniGorunmezVeTeklifAlmaz() {
        User sahip = mk("aski-takas");
        User ben = mk("aski-teklifci");
        SwapBook hedef = swapService.open(sahip, book(), null);
        SwapBook benim = swapService.open(ben, book(), null);
        askiya(sahip);

        assertThat(swapService.discover(ben, hedef.getBook().getTitle()))
                .extracting(SwapBook::getId).doesNotContain(hedef.getId());
        assertThatThrownBy(() -> swapService.offer(hedef.getId(), benim.getId(), ben, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void askidakiUyeninIstegiGorunmezVeKarsilanamaz() {
        User isteyen = mk("aski-isteyen");
        BookRequest r = requestService.create(isteyen, book(), null);
        askiya(isteyen);

        assertThat(requestService.openRequests(r.getBook().getTitle()))
                .extracting(BookRequest::getId).doesNotContain(r.getId());
        assertThatThrownBy(() -> requestService.fulfill(r.getId(), mk("aski-karsilayan"), DonationSource.OWN))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Yönetimin kaldırdığı ilan ----------

    @Test
    void yonetiminKaldirdigiBagisiSahibiYenidenAcamaz() {
        User bagisci = mk("mod-bagisci");
        Donation d = donationService.create(bagisci, book(), 2, TargetLevel.HEPSI, DonationSource.OWN, null);
        admin.removeDonation(d.getId(), "uygunsuz");

        assertThatThrownBy(() -> donationService.reopen(d.getId(), bagisci))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yönetim");
        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);
    }

    @Test
    void yonetiminKaldirdigiBagisTakasaAktarilarakYenidenYayinlanamaz() {
        User bagisci = mk("mod-bagis-takas");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, "uygunsuz metin");
        admin.removeDonation(d.getId(), "uygunsuz");

        assertThatThrownBy(() -> donationService.moveToSwap(d.getId(), bagisci, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yönetim");
        assertThat(swapBooks.findByUserAndBook_Id(bagisci, d.getBook().getId())).isEmpty();
        assertThat(donations.findById(d.getId())).isPresent();
    }

    @Test
    void yonetiminKaldirdigiBagisDetayiYalnizcaSahibineVeYoneticiyeAcilir() {
        User bagisci = mk("mod-detay");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        admin.removeDonation(d.getId(), null);
        User yonetici = mk("mod-detay-admin");
        yonetici.setAdmin(true);
        users.save(yonetici);

        assertThat(donationService.view(d.getId(), null)).isEmpty();
        assertThat(donationService.view(d.getId(), mk("mod-detay-yabanci"))).isEmpty();
        assertThat(donationService.view(d.getId(), bagisci)).isPresent();
        assertThat(donationService.view(d.getId(), yonetici)).isPresent();
    }

    @Test
    void kaldirilanBagistakiTalepIptaliBagisiYenidenAcmaz() {
        User bagisci = mk("mod-iptal");
        User alici = ogrenci("mod-iptal-alici");
        Donation d = donationService.create(bagisci, book(), 2, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        admin.removeDonation(d.getId(), null);

        donationService.cancelClaim(c.getId(), alici);

        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);
    }

    @Test
    void bagiscininKapattigiBagisTalepIptalindeKapaliKalir() {
        User bagisci = mk("kapat-bagisci");
        User alici = ogrenci("kapat-alici");
        Donation d = donationService.create(bagisci, book(), 3, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        donationService.close(d.getId(), bagisci);

        donationService.cancelClaim(c.getId(), alici);

        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);
    }

    @Test
    void dolduguIcinKapananBagisTalepIptalindeYenidenAcilir() {
        User bagisci = mk("dolu-bagisci");
        User alici = ogrenci("dolu-alici");
        Donation d = donationService.create(bagisci, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);

        donationService.cancelClaim(c.getId(), alici);

        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.OPEN);
    }

    @Test
    void yonetiminKaldirdigiTakasIlaniYenidenAcilamazVeBagisaAktarilamaz() {
        User sahip = mk("mod-takas");
        SwapBook s = swapService.open(sahip, book(), null);
        admin.removeSwapBook(s.getId(), null);

        assertThatThrownBy(() -> swapService.setStatus(s.getId(), sahip, SwapBookStatus.OPEN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> swapService.moveToDonation(s.getId(), sahip, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(swapBooks.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);
    }

    // ---------- Öğrenci onayı ----------

    @Test
    void okulEpostasiBekleyenUyeBelgeListesindeGorunmezVeOnaylanamaz() {
        User u = mk("eposta-bekleyen");
        u.setStudentStatus(StudentStatus.PENDING);
        u.setStudentEmail("ogr-" + UUID.randomUUID() + "@ogr.deu.edu.tr");
        users.save(u);

        assertThat(admin.pendingDocuments()).extracting(User::getId).doesNotContain(u.getId());
        assertThatThrownBy(() -> admin.approveStudent(u.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(u.getId()).orElseThrow().getStudentStatus()).isEqualTo(StudentStatus.PENDING);
    }

    // ---------- Bildirimler ----------

    @Test
    void tumunuOkunduSon50IleSinirliKalmaz() {
        User u = mk("bildirim");
        for (int i = 0; i < 60; i++) notifications.notify(u, "test", "bildirim " + i);
        assertThat(notifications.unreadCount(u)).isEqualTo(60);

        assertThat(notifications.markAllRead(u)).isEqualTo(60);
        assertThat(notifications.unreadCount(u)).isZero();
    }
}
