package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "Kitabı alabilir mi?" kuralları: öncelik penceresi, adres, seviye, kota, kendi bağışı. */
@SpringBootTest
@ActiveProfiles("test")
class DonationEligibilityTest {

    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository notifications;

    private User user(String tag, boolean student, String address, SchoolLevel level) {
        User u = new User();
        u.setName("Test " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress(address);
        if (student) u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(level);
        return users.save(u);
    }

    private Donation donation(User donor, TargetLevel level, int qty) {
        Book b = new Book();
        b.setTitle("Kitap " + UUID.randomUUID());
        b.setAuthor("Yazar");
        b = books.save(b);

        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(b);
        d.setQuantity(qty);
        d.setTargetLevel(level);
        return donations.save(d);
    }

    /** Öncelik penceresini geçmişe alır (createdAt updatable=false olduğu için SQL ile). */
    private void backdate(Donation d, long days) {
        jdbc.update("UPDATE donations SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)), d.getId());
    }

    private ClaimEligibility check(Donation d, User u) {
        return donationService.eligibility(donationService.view(d.getId()).orElseThrow(), u);
    }

    @Test
    void yeniBagisOncelikPenceresindeUyeyeKapali() {
        User donor = user("donor", false, "Adres", null);
        User uye = user("uye", false, "Adres", null);
        Donation d = donation(donor, TargetLevel.HEPSI, 2);

        ClaimEligibility e = check(d, uye);
        assertThat(e.allowed()).isFalse();
        assertThat(e.code()).isEqualTo("PRIORITY_WINDOW");
    }

    @Test
    void yeniBagisOgrenciyeAcik() {
        User donor = user("donor", false, "Adres", null);
        User ogrenci = user("ogr", true, "Adres", SchoolLevel.LISE);
        Donation d = donation(donor, TargetLevel.HEPSI, 2);

        assertThat(check(d, ogrenci).allowed()).isTrue();
    }

    @Test
    void pencereDolduktanSonraUyeDeAlabilir() {
        User donor = user("donor", false, "Adres", null);
        User uye = user("uye", false, "Adres", null);
        Donation d = donation(donor, TargetLevel.HEPSI, 2);
        backdate(d, 3);

        assertThat(check(d, uye).allowed()).isTrue();
    }

    @Test
    void adressizKullaniciAlamaz() {
        User donor = user("donor", false, "Adres", null);
        User adressiz = user("adressiz", true, null, SchoolLevel.LISE);
        Donation d = donation(donor, TargetLevel.HEPSI, 1);

        ClaimEligibility e = check(d, adressiz);
        assertThat(e.allowed()).isFalse();
        assertThat(e.code()).isEqualTo("ADDRESS_REQUIRED");
    }

    @Test
    void seviyeUyusmazsaAlamaz() {
        User donor = user("donor", false, "Adres", null);
        User ortaokul = user("orta", true, "Adres", SchoolLevel.ORTAOKUL);
        Donation d = donation(donor, TargetLevel.UNIVERSITE, 1);

        ClaimEligibility e = check(d, ortaokul);
        assertThat(e.allowed()).isFalse();
        assertThat(e.code()).isEqualTo("LEVEL_MISMATCH");
    }

    @Test
    void kendiBagisindanAlamaz() {
        User donor = user("donor", true, "Adres", SchoolLevel.LISE);
        Donation d = donation(donor, TargetLevel.HEPSI, 1);

        assertThat(check(d, donor).code()).isEqualTo("OWN_DONATION");
    }

    @Test
    void girisYapmayanAlamaz() {
        User donor = user("donor", false, "Adres", null);
        Donation d = donation(donor, TargetLevel.HEPSI, 1);

        assertThat(check(d, null).code()).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void claimAdediDusurulurVeTukeninceBagisKapanir() {
        User donor = user("donor", false, "Adres", null);
        User ogrenci = user("ogr", true, "Adres", SchoolLevel.LISE);
        Donation d = donation(donor, TargetLevel.HEPSI, 1);

        donationService.claim(d.getId(), ogrenci);

        DonationView v = donationService.view(d.getId()).orElseThrow();
        assertThat(v.getRemaining()).isZero();
        assertThat(v.donation().getStatus()).isEqualTo(DonationStatus.CLOSED);
        // aynı kullanıcı ikinci kez alamaz
        assertThat(check(d, ogrenci).allowed()).isFalse();
    }

    @Test
    void uyeHaftalikKotasiDoluncaAlamaz() {
        User donor = user("donor", false, "Adres", null);
        User uye = user("uye", false, "Adres", null);

        Donation ilk = donation(donor, TargetLevel.HEPSI, 1);
        backdate(ilk, 3);
        donationService.claim(ilk.getId(), uye);   // üye kotası: haftada 1

        Donation ikinci = donation(donor, TargetLevel.HEPSI, 1);
        backdate(ikinci, 3);
        ClaimEligibility e = check(ikinci, uye);
        assertThat(e.allowed()).isFalse();
        assertThat(e.code()).isEqualTo("QUOTA_FULL");
    }

    @Test
    void filtreSeviyeVeAramayaGoreSuzer() {
        User donor = user("donor", false, "Adres", null);
        Donation uni = donation(donor, TargetLevel.UNIVERSITE, 1);
        String baslik = uni.getBook().getTitle();

        var ortaokulSonuc = donationService.openDonations(
                new DonationService.Filter(TargetLevel.ORTAOKUL, baslik, null, true));
        assertThat(ortaokulSonuc).isEmpty();

        var uniSonuc = donationService.openDonations(
                new DonationService.Filter(TargetLevel.UNIVERSITE, baslik, null, true));
        assertThat(uniSonuc).hasSize(1);
    }

    @Test
    void claimSonrasiBagiscayaBildirimGider() {
        User donor = user("donor", false, "Adres", null);
        User ogrenci = user("ogr", true, "Adres", SchoolLevel.LISE);
        Donation d = donation(donor, TargetLevel.HEPSI, 1);

        donationService.claim(d.getId(), ogrenci);

        var list = notifications.findTop50ByUserOrderByCreatedAtDesc(donor);
        assertThat(list).isNotEmpty();
        assertThat(list.get(0).getType()).isEqualTo("donation_claimed");
        assertThat(list.get(0).getMessage()).contains(ogrenci.getName());
        assertThat(notifications.countByUserAndReadFlagFalse(donor)).isPositive();
    }
}
