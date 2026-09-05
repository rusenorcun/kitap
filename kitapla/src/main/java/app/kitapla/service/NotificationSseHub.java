package app.kitapla.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kullanıcı başına açık bildirim SSE bağlantılarını tutar ve yeni bildirim olduğunda haber verir.
 */
@Component
public class NotificationSseHub {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseHub.class);

    private final Map<Long, List<SseEmitter>> aboneler = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> liste = aboneler.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        liste.add(emitter);

        Runnable temizle = () -> {
            liste.remove(emitter);
            if (liste.isEmpty()) aboneler.remove(userId, liste);
        };
        emitter.onCompletion(temizle);
        emitter.onTimeout(temizle);
        emitter.onError(e -> temizle.run());

        try {
            // İlk olay: mevcut okunmamış bildirim sayısını hemen iletir
            emitter.send(SseEmitter.event().name("bildirim").data("{\"unread\":" + unreadCount + "}"));
        } catch (Exception ex) {
            temizle.run();
        }
        return emitter;
    }

    /** Kullanıcının tüm açık sekmelerine güncel okunmamış sayısını bildirir. */
    public void publish(Long userId, long unreadCount) {
        List<SseEmitter> liste = aboneler.get(userId);
        if (liste == null || liste.isEmpty()) return;
        List<SseEmitter> kopmus = new ArrayList<>();
        for (SseEmitter e : liste) {
            try {
                e.send(SseEmitter.event().name("bildirim").data("{\"unread\":" + unreadCount + "}"));
            } catch (Exception ex) {
                log.debug("Bildirim SSE gönderilemedi, abone listeden temizleniyor: {}", ex.getMessage());
                kopmus.add(e);
            }
        }
        if (!kopmus.isEmpty()) {
            liste.removeAll(kopmus);
            if (liste.isEmpty()) aboneler.remove(userId, liste);
        }
    }

    /** 20 saniyede bir boşta kalan bağlantıları canlı tutmak için ping yorumu gönderir. */
    @Scheduled(fixedDelay = 20000)
    public void ping() {
        if (aboneler.isEmpty()) return;
        for (Map.Entry<Long, List<SseEmitter>> entry : aboneler.entrySet()) {
            List<SseEmitter> liste = entry.getValue();
            if (liste == null || liste.isEmpty()) continue;
            List<SseEmitter> kopmus = new ArrayList<>();
            for (SseEmitter e : liste) {
                try {
                    e.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    kopmus.add(e);
                }
            }
            if (!kopmus.isEmpty()) {
                liste.removeAll(kopmus);
                if (liste.isEmpty()) aboneler.remove(entry.getKey(), liste);
            }
        }
    }

    public int subscriberCount(Long userId) {
        List<SseEmitter> liste = aboneler.get(userId);
        return liste == null ? 0 : liste.size();
    }
}