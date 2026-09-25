package app.kitappla.repo;

import app.kitappla.domain.Notification;
import app.kitappla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);
    long countByUserAndReadFlagFalse(User user);

    /** Tüm okunmamışları tek sorguda okundu yapar (listede görünen son 50 ile sınırlı değil). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update Notification n set n.readFlag = true where n.user = :user and n.readFlag = false")
    int markAllRead(@org.springframework.data.repository.query.Param("user") User user);

    /** Üye silinirken bildirimleri de gider (yabancı anahtar bağı kalmasın). */
    void deleteByUser(User user);
}
