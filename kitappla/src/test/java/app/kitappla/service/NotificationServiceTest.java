package app.kitappla.service;

import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Bildirim okuma/işaretleme. */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired NotificationService notifications;
    @Autowired UserRepository users;

    private User mk(String tag) {
        User u = new User();
        u.setName("Bildirim " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    @Test
    void okunmamisSayisiVeTekTekIsaretleme() {
        User u = mk("tek");
        notifications.notify(u, "test", "Birinci");
        notifications.notify(u, "test", "İkinci");
        assertThat(notifications.unreadCount(u)).isEqualTo(2);

        var ilk = notifications.latest(u).get(0);
        notifications.markRead(u, ilk.getId());
        assertThat(notifications.unreadCount(u)).isEqualTo(1);
    }

    @Test
    void hepsiniOkunduIsaretleme() {
        User u = mk("hepsi");
        notifications.notify(u, "test", "A");
        notifications.notify(u, "test", "B");
        notifications.notify(u, "test", "C");

        assertThat(notifications.markAllRead(u)).isEqualTo(3);
        assertThat(notifications.unreadCount(u)).isZero();
        assertThat(notifications.markAllRead(u)).isZero();   // tekrar çağırınca 0
    }

    @Test
    void baskasininBildirimiIsaretlenemez() {
        User sahip = mk("sahip");
        User yabanci = mk("yabanci");
        notifications.notify(sahip, "test", "Gizli");

        var n = notifications.latest(sahip).get(0);
        notifications.markRead(yabanci, n.getId());
        assertThat(notifications.unreadCount(sahip)).isEqualTo(1);   // değişmedi
    }

    @Test
    void bildirimeTiklandigindaOkunurVeHedefBaglantiDoner() {
        User u = mk("link");
        notifications.notify(u, "mesaj", "Yeni bir mesajınız var", "/mesajlar/42");
        var n = notifications.latest(u).get(0);
        assertThat(n.getLink()).isEqualTo("/mesajlar/42");
        assertThat(n.isReadFlag()).isFalse();

        String targetLink = notifications.consume(u, n.getId());
        assertThat(targetLink).isEqualTo("/mesajlar/42");
        assertThat(notifications.unreadCount(u)).isZero();
    }

    @Test
    void baglantisizEskiBildirimIcinGeriyeDonukBaglantiCozulur() {
        User u = mk("fallback");
        // link parametresi olmadan oluşturulan eski bildirimler
        notifications.notify(u, "mesaj", "Ali sana mesaj gönderdi");
        var n = notifications.latest(u).get(0);
        assertThat(n.getLink()).isNull();
        // Fallback resolver devreye girmeli
        assertThat(n.getResolvedLink()).isEqualTo("/mesajlar");

        String targetLink = notifications.consume(u, n.getId());
        assertThat(targetLink).isEqualTo("/mesajlar");
        assertThat(notifications.unreadCount(u)).isZero();
    }
}
