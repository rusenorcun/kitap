package app.kitapla.web;

import app.kitapla.config.Features;
import app.kitapla.domain.User;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.service.MessageService;
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
    private final Features features;
    private final MessageService messages;

    public GlobalModelAdvice(NotificationRepository notifications, Features features,
                             MessageService messages) {
        this.notifications = notifications;
        this.features = features;
        this.messages = messages;
    }

    /** Nav'daki mesaj rozeti: okunmamış mesajı olan sohbet sayısı. */
    @ModelAttribute("unreadMessages")
    public long unreadMessages() {
        User user = currentUser();
        return user == null ? 0 : messages.unreadConversations(user);
    }

    /** Şablonlar açık/kapalı özelliklere göre farklı metin ve düğme gösterir. */
    @ModelAttribute("features")
    public Features features() {
        return features;
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
