package app.kitapla.repo;

import app.kitapla.domain.Notification;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);
    long countByUserAndReadFlagFalse(User user);
}
