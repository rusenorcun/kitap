package app.kitapla.service;

import app.kitapla.domain.Notification;
import app.kitapla.domain.User;
import app.kitapla.repo.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final NotificationSseHub sseHub;

    public NotificationService(NotificationRepository repo, NotificationSseHub sseHub) {
        this.repo = repo;
        this.sseHub = sseHub;
    }

    public void notify(User user, String type, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setMessage(message);
        repo.save(n);
        sseHub.publish(user.getId(), unreadCount(user));
    }

    public List<Notification> latest(User user) {
        return repo.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    public long unreadCount(User user) {
        return repo.countByUserAndReadFlagFalse(user);
    }

    /** Tek bildirimi okundu işaretler (yalnızca sahibi). */
    @Transactional
    public void markRead(User user, Long id) {
        repo.findById(id)
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .ifPresent(n -> { n.setReadFlag(true); repo.save(n); });
        sseHub.publish(user.getId(), unreadCount(user));
    }

    /** Kullanıcının tüm bildirimlerini okundu işaretler. */
    @Transactional
    public int markAllRead(User user) {
        var unread = repo.findTop50ByUserOrderByCreatedAtDesc(user).stream()
                .filter(n -> !n.isReadFlag()).toList();
        unread.forEach(n -> n.setReadFlag(true));
        repo.saveAll(unread);
        sseHub.publish(user.getId(), 0);
        return unread.size();
    }
}
