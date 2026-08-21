package app.kitapla.service;

import app.kitapla.domain.Notification;
import app.kitapla.domain.User;
import app.kitapla.repo.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void notify(User user, String type, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setMessage(message);
        repo.save(n);
    }

    public List<Notification> latest(User user) {
        return repo.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    public long unreadCount(User user) {
        return repo.countByUserAndReadFlagFalse(user);
    }
}
