package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Her sayfaya oturumdaki kullanıcıyı ve okunmamış bildirim sayısını ekler (nav için).
 * Kullanıcı veritabanından tazelenir; böylece admin onayı sonrası öğrenci durumu
 * yeniden giriş gerektirmeden görünür.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository users;
    private final NotificationRepository notifications;

    public GlobalModelAdvice(UserRepository users, NotificationRepository notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        User session = CurrentUser.get();
        if (session == null) return null;
        return users.findById(session.getId()).orElse(session);
    }

    @ModelAttribute("unreadCount")
    public long unreadCount() {
        User user = currentUser();
        return user == null ? 0 : notifications.countByUserAndReadFlagFalse(user);
    }
}
