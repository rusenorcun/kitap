package app.kitapla.web;

import app.kitapla.config.Features;
import app.kitapla.domain.User;
import app.kitapla.service.MessageService;
import app.kitapla.service.NotificationService;
import app.kitapla.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Her HTML sayfasına oturumdaki kullanıcıyı ve nav rozetlerini ekler.
 * <p>
 * Yalnızca {@code app.kitapla.web} denetleyicilerine uygulanır: REST API yanıtları
 * model kullanmaz, bu değerleri orada hesaplamak her API isteğine boşuna sorgu eklerdi.
 * Bildirim rozeti önbellekten gelir; mesaj rozeti rol değişikliğine anında uymak için her
 * seferinde sorgulanır. Kullanıcıyı FreshPrincipalFilter zaten tazeler.
 */
@ControllerAdvice(basePackages = "app.kitapla.web")
public class GlobalModelAdvice {

    private final NotificationService notifications;
    private final Features features;
    private final MessageService messages;

    public GlobalModelAdvice(NotificationService notifications, Features features,
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

    /** Kayıt ve profil formlarındaki okul listesi. */
    @ModelAttribute("okullar")
    public app.kitapla.domain.School[] okullar() {
        return app.kitapla.domain.School.values();
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        return CurrentUser.get();
    }

    @ModelAttribute("unreadCount")
    public long unreadCount() {
        User user = currentUser();
        return user == null ? 0 : notifications.unreadCount(user);
    }
}
