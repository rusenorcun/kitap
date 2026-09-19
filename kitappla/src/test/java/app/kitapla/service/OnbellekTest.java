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

/** Nav rozetleri önbellekten gelir ve veri servis üzerinden değişince tazelenir. */
@SpringBootTest
@ActiveProfiles("test")
class OnbellekTest {

    @Autowired NotificationService notifications;
    @Autowired NotificationRepository notificationRepo;
    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired ReportRepository reports;
    @Autowired PasswordEncoder encoder;

    private User uye(String etiket, boolean yonetici) {
        User u = new User();
        u.setName("Önbellek " + etiket);
        u.setEmail(etiket + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(SchoolLevel.LISE);
        u.setAdmin(yonetici);
        return users.save(u);
    }

    private Conversation talepSohbeti(User bagisci, User alici) {
        Book b = new Book();
        b.setTitle("Önbellek kitabı " + UUID.randomUUID());
        Donation d = donationService.create(bagisci, books.save(b), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        return messages.open(ConversationKind.CLAIM, c.getId(), alici);
    }

    @Test
    void okunmamisBildirimSayisiOnbellektenGelirVeServisYazinincaTazelenir() {
        User u = uye("bildirim", false);
        notifications.notify(u, "deneme", "bir");
        assertThat(notifications.unreadCount(u)).isEqualTo(1);

        // Servisi atlayan yazım önbelleği silmez: sayı önbellekten gelmeye devam eder
        Notification dogrudan = new Notification();
        dogrudan.setUser(u);
        dogrudan.setType("deneme");
        dogrudan.setMessage("iki");
        notificationRepo.save(dogrudan);
        assertThat(notifications.unreadCount(u)).isEqualTo(1);

        // Servis üzerinden yazım kaydı siler; sayı veritabanından yeniden okunur
        notifications.notify(u, "deneme", "üç");
        assertThat(notifications.unreadCount(u)).isEqualTo(3);

        notifications.markAllRead(u);
        assertThat(notifications.unreadCount(u)).isZero();
    }

    @Test
    void okunmamisSohbetSayisiMesajVeOkumaIleTazelenir() {
        User bagisci = uye("sohbet-bagisci", false);
        User alici = uye("sohbet-alici", false);
        Conversation s = talepSohbeti(bagisci, alici);
        assertThat(messages.unreadConversations(bagisci)).isZero();

        messages.send(s.getId(), alici, "Merhaba");
        assertThat(messages.unreadConversations(bagisci)).isEqualTo(1);
        assertThat(messages.unreadConversations(alici)).isZero();

        messages.markRead(messages.require(s.getId(), bagisci), bagisci);
        assertThat(messages.unreadConversations(bagisci)).isZero();
    }

    @Test
    void tarafOlmayanYoneticiSikayetSohbetindekiMesajiOkunmamisGorur() {
        User sikayetci = uye("sikayetci", false);
        User hedef = uye("hedef", false);
        User yonetici = uye("yonetici", true);
        long once = messages.unreadConversations(yonetici);

        Report r = new Report();
        r.setReporter(sikayetci);
        r.setKind(ReportKind.USER);
        r.setRefId(hedef.getId());
        r.setReason(ReportReason.SPAM);
        r.setStatus(ReportStatus.OPEN);
        r.setReportedUser(hedef);
        r = reports.save(r);

        Conversation s = messages.open(ConversationKind.REPORT, r.getId(), sikayetci);
        messages.send(s.getId(), sikayetci, "Yardım eder misiniz?");
        assertThat(messages.unreadConversations(yonetici)).isEqualTo(once + 1);

        // Yöneticiler şikâyet sohbetinde aynı okuma damgasını paylaşır
        messages.markRead(messages.require(s.getId(), yonetici), yonetici);
        assertThat(messages.unreadConversations(yonetici)).isEqualTo(once);
    }
}
