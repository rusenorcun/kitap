package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Her sayfaya oturumdaki kullanıcıyı ve okunmamış bildirim sayısını ekler (nav için).
 * Kullanıcıyı FreshPrincipalFilter her istekte veritabanından tazelediği için
 * burada ayrıca sorgu yapılmaz.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NotificationRepository notifications;

    public GlobalModelAdvice(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        return CurrentUser.get();
    }

    @ModelAttribute("unreadCount")
    public long unreadCount() {
        User user = currentUser();
        return user == null ? 0 : notifications.countByUserAndReadFlagFalse(user);
    }
}
