package app.kitapla.service;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
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
}
