package app.kitappla.service;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import app.kitappla.config.OnbellekConfig;
import app.kitappla.domain.Notification;
import app.kitappla.domain.User;
import app.kitappla.repo.NotificationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /** notifications.message sütunu 500 karakter; taşan metin kaydı düşürürdü. */
    private static final int MAX_MESAJ = 500;

    @CacheEvict(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id", condition = "#user != null")
    public void notify(User user, String type, String message) {
        notify(user, type, message, null);
    }

    @CacheEvict(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id", condition = "#user != null")
    public void notify(User user, String type, String message, String link) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setMessage(kisalt(message));
        n.setLink(link);
        repo.save(n);
        commitSonrasi(() -> sseHub.publish(user.getId(), unreadCount(user)));
    }

    public List<Notification> latest(User user) {
        return repo.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    /** Her sayfada nav rozeti için okunur; önbellekten gelir, bildirim değişince silinir. */
    @Cacheable(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id")
    public long unreadCount(User user) {
        return repo.countByUserAndReadFlagFalse(user);
    }

    /** Tek bildirimi okundu işaretler (yalnızca sahibi). */
    @Transactional
    @CacheEvict(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id")
    public void markRead(User user, Long id) {
        repo.findById(id)
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .ifPresent(n -> { n.setReadFlag(true); repo.save(n); });
        commitSonrasi(() -> sseHub.publish(user.getId(), unreadCount(user)));
    }

    /**
     * Bildirime tıklandığında: okundu işaretler ve yönlendirilecek hedef bağlantıyı döner.
     */
    @Transactional
    @CacheEvict(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id")
    public String consume(User user, Long id) {
        if (user == null || id == null) return "/panom";
        Notification n = repo.findById(id)
                .filter(notif -> notif.getUser().getId().equals(user.getId()))
                .orElse(null);
        if (n == null) return "/panom";
        if (!n.isReadFlag()) {
            n.setReadFlag(true);
            repo.save(n);
            commitSonrasi(() -> sseHub.publish(user.getId(), unreadCount(user)));
        }
        return n.getResolvedLink();
    }

    /** Kullanıcının tüm bildirimlerini okundu işaretler. */
    @Transactional
    @CacheEvict(cacheNames = OnbellekConfig.OKUNMAMIS_BILDIRIM, key = "#user.id")
    public int markAllRead(User user) {
        // Liste yalnızca son 50'yi gösterir; işaretleme hepsine uygulanmalı, yoksa rozet sıfırlanmaz
        int n = repo.markAllRead(user);
        commitSonrasi(() -> sseHub.publish(user.getId(), 0));
        return n;
    }

    /**
     * Bildirim metinleri kitap başlığı, yönetici notu gibi kullanıcı girdisi taşır; bunlar
     * tek başına sütun sınırından uzun olabiliyor. Taşan metin veritabanı hatasına yol açıp
     * çağıran işlemi (şikâyet sonuçlandırma, ilan kaldırma) geri alırdı; bu yüzden kırpılır.
     */
    private static String kisalt(String message) {
        if (message == null) return null;
        return message.length() <= MAX_MESAJ ? message : message.substring(0, MAX_MESAJ - 1) + "…";
    }

    /**
     * Açık sekmelere yayını işlem commit olduktan sonra yapar: yazım sürerken veritabanı bağlantısı
     * SSE'ye yazmayı beklemez ve iletilen sayı kesinleşmiş veriden okunur. İşlem yoksa hemen çalışır.
     */
    static void commitSonrasi(Runnable is) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    is.run();
                }
            });
        } else {
            is.run();
        }
    }
}
