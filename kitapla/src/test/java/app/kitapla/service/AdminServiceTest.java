package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Belge onayı, üye yönetimi ve içerik moderasyonu. */
@SpringBootTest
@ActiveProfiles("test")
class AdminServiceTest {

    @Autowired AdminService admin;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired BookRequestRepository requests;
    @Autowired SwapBookRepository swapBooks;
    @Autowired SwapOfferRepository swapOffers;
    @Autowired PasswordEncoder encoder;
    @Value("${kitapla.upload-dir}") String uploadDir;

    private User mk(String tag) {
        User u = new User();
        u.setName("Yonetim " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        return users.save(u);
    }

    private User mkAdmin(String tag) {
        User u = mk(tag);
        u.setAdmin(true);
        return users.save(u);
    }

    /** İncelemede, diskte gerçek belgesi olan bir üye. */
    private User mkPending(String tag) throws Exception {
        User u = mk(tag);
        Path dir = Path.of(uploadDir, "documents");
        Files.createDirectories(dir);
        String fileName = "test-" + UUID.randomUUID() + ".txt";
        Files.writeString(dir.resolve(fileName), "belge");
        u.setStudentStatus(StudentStatus.PENDING);
        u.setSchoolLevel(SchoolLevel.LISE);
        u.setDocumentNo("DOC-" + UUID.randomUUID());
        u.setDocumentPath(fileName);
        return users.save(u);
    }

    private Book book(String title) {
        Book b = new Book();
        b.setTitle(title);
        b.setAuthor("Yazar");
        return books.save(b);
    }

    private Donation donation(User donor, String title) {
        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(book(title));
        d.setQuantity(1);
        return donations.save(d);
    }

    // ---------- Belge onayı ----------

    @Test
    void belgeOnaylanincaOgrenciOlurVeBildirimGider() throws Exception {
        User u = mkPending("onay");
        admin.approveStudent(u.getId());

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.APPROVED);
        assertThat(saved.isStudent()).isTrue();
        assertThat(notifications.latest(saved))
                .anyMatch(n -> n.getMessage().contains("onaylandı"));
    }

    @Test
    void belgeReddedilinceDosyaSilinirVeTekrarBasvurulabilir() throws Exception {
        User u = mkPending("ret");
        Path file = Path.of(uploadDir, "documents", u.getDocumentPath());
        assertThat(Files.exists(file)).isTrue();

        admin.rejectStudent(u.getId(), "Belge okunaklı değil.");

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.REJECTED);
        assertThat(saved.getDocumentPath()).isNull();
        assertThat(Files.exists(file)).as("reddedilen belge diskte kalmamalı").isFalse();
        assertThat(notifications.latest(saved))
                .anyMatch(n -> n.getMessage().contains("Belge okunaklı değil."));
    }

    @Test
    void sonuclanmisBasvuruTekrarIslemeAlinmaz() throws Exception {
        User u = mkPending("iki-kez");
        admin.approveStudent(u.getId());

        assertThatThrownBy(() -> admin.approveStudent(u.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten sonuçlanmış");
    }

    @Test
    void belgeYoluDosyaDizinininDisinaCikamaz() {
        User u = mk("yol");
        u.setDocumentPath("../../gizli.txt");
        users.save(u);

        assertThatThrownBy(() -> admin.documentPathOf(u.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geçersiz");
    }

    @Test
    void belgesiOlmayanUyeIcinYolCozulemez() {
        User u = mk("belgesiz");
        assertThatThrownBy(() -> admin.documentPathOf(u.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yüklü belgesi yok");
    }

    // ---------- Üye yönetimi ----------

    @Test
    void uyeAskiyaAlinirVeGeriAlinir() {
        User yonetici = mkAdmin("blok-admin");
        User u = mk("blok");

        admin.setBlocked(yonetici, u.getId(), true);
        assertThat(users.findById(u.getId()).orElseThrow().isBlocked()).isTrue();

        admin.setBlocked(yonetici, u.getId(), false);
        assertThat(users.findById(u.getId()).orElseThrow().isBlocked()).isFalse();
        assertThat(notifications.latest(u)).hasSize(2);
    }

    @Test
    void yoneticiKendiniAskiyaAlamaz() {
        User yonetici = mkAdmin("kendi");
        assertThatThrownBy(() -> admin.setBlocked(yonetici, yonetici.getId(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kendini");
    }

    @Test
    void yoneticiAskiyaAlinamaz() {
        User yonetici = mkAdmin("a1");
        User digerYonetici = mkAdmin("a2");

        assertThatThrownBy(() -> admin.setBlocked(yonetici, digerYonetici.getId(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Yöneticiler askıya alınamaz");
    }

    @Test
    void yetkiVerilirVeAlinir() {
        User yonetici = mkAdmin("yetki-veren");
        User u = mk("yetki-alan");

        admin.setAdmin(yonetici, u.getId(), true);
        assertThat(users.findById(u.getId()).orElseThrow().isAdmin()).isTrue();

        admin.setAdmin(yonetici, u.getId(), false);
        assertThat(users.findById(u.getId()).orElseThrow().isAdmin()).isFalse();
    }

    @Test
    void askidakiUyeyeYetkiVerilemez() {
        User yonetici = mkAdmin("y");
        User u = mk("askida");
        admin.setBlocked(yonetici, u.getId(), true);

        assertThatThrownBy(() -> admin.setAdmin(yonetici, u.getId(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Askıdaki");
    }

    @Test
    void kayitsizUyeSilinir() {
        User yonetici = mkAdmin("silen");
        User u = mk("silinecek");

        admin.deleteUser(yonetici, u.getId());
        assertThat(users.findById(u.getId())).isEmpty();
    }

    @Test
    void kaydiOlanUyeSilinemez() {
        User yonetici = mkAdmin("silen2");
        User u = mk("bagisci");
        donation(u, "Silinemez Kitap");

        assertThatThrownBy(() -> admin.deleteUser(yonetici, u.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("askıya al");
        assertThat(users.findById(u.getId())).isPresent();
    }

    @Test
    void yoneticiSilinemez() {
        User yonetici = mkAdmin("silen3");
        User digeri = mkAdmin("silinemez");

        assertThatThrownBy(() -> admin.deleteUser(yonetici, digeri.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Yönetici silinemez");
    }

    // ---------- İçerik moderasyonu ----------

    @Test
    void bagisKaldirilincaKapanirVeBagisciyaBildirimGider() {
        User donor = mk("moderasyon");
        Donation d = donation(donor, "Kaldırılacak Kitap");

        admin.removeDonation(d.getId(), "Uygunsuz açıklama.");

        assertThat(donations.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.CLOSED);
        assertThat(notifications.latest(donor))
                .anyMatch(n -> n.getMessage().contains("Kaldırılacak Kitap")
                        && n.getMessage().contains("Uygunsuz açıklama."));
    }

    @Test
    void kapaliBagisTekrarKaldirilamaz() {
        User donor = mk("kapali");
        Donation d = donation(donor, "Kapalı Kitap");
        admin.removeDonation(d.getId(), null);

        assertThatThrownBy(() -> admin.removeDonation(d.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten kapalı");
    }

    @Test
    void acikIstekKaldirilirVeSahibineBildirimGider() {
        User student = mk("isteyen");
        BookRequest r = new BookRequest();
        r.setStudent(student);
        r.setBook(book("İstenen Kitap"));
        requests.save(r);

        admin.removeRequest(r.getId(), "Kural dışı.");

        assertThat(requests.findById(r.getId())).isEmpty();
        assertThat(notifications.latest(student))
                .anyMatch(n -> n.getMessage().contains("İstenen Kitap"));
    }

    @Test
    void takasIlaniKaldirilir() {
        User owner = mk("takasci");
        SwapBook s = new SwapBook();
        s.setUser(owner);
        s.setBook(book("Takas Kitabı"));
        swapBooks.save(s);

        admin.removeSwapBook(s.getId(), null);

        assertThat(swapBooks.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);
        assertThat(notifications.latest(owner))
                .anyMatch(n -> n.getMessage().contains("Takas Kitabı"));
    }

    @Test
    void acikTeklifiOlanTakasIlaniKaldirilamaz() {
        User owner = mk("teklifli");
        User other = mk("teklif-veren");

        SwapBook hedef = new SwapBook();
        hedef.setUser(owner);
        hedef.setBook(book("Teklifli Kitap"));
        swapBooks.save(hedef);

        SwapBook teklifEdilen = new SwapBook();
        teklifEdilen.setUser(other);
        teklifEdilen.setBook(book("Teklif Edilen Kitap"));
        swapBooks.save(teklifEdilen);

        SwapOffer o = new SwapOffer();
        o.setFromUser(other);
        o.setToUser(owner);
        o.setOfferedSwapBook(teklifEdilen);
        o.setTargetSwapBook(hedef);
        swapOffers.save(o);

        assertThatThrownBy(() -> admin.removeSwapBook(hedef.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("açık teklif");
        assertThat(swapBooks.findById(hedef.getId()).orElseThrow().getStatus())
                .isEqualTo(SwapBookStatus.OPEN);
    }

    @Test
    void sayaclarGercekVeriyiYansitir() {
        long oncekiAcikBagis = admin.stats().openDonations();
        User donor = mk("sayac");
        donation(donor, "Sayaç Kitabı");

        AdminStats s = admin.stats();
        assertThat(s.openDonations()).isEqualTo(oncekiAcikBagis + 1);
        assertThat(s.users()).isEqualTo(users.count());
    }
}
