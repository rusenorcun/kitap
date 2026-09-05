package app.kitapla.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sohbet başına açık SSE bağlantılarını tutar ve yeni mesaj olduğunda haber verir.
 * <p>
 * Bellek içidir: tek örnekli kurulum için yeterlidir. Uygulama yeniden başlarsa
 * tarayıcı bağlantıyı kendisi yeniler (EventSource otomatik yeniden bağlanır).
 * Sunucu birden fazla kopya olarak çalıştırılacaksa buranın yerine bir mesaj
 * kuyruğu (Redis vb.) gerekir.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<Long, List<SseEmitter>> aboneler = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long conversationId) {
        // Uzun ömürlü bağlantı (30 dk); tarayıcı kopmada kendisi yeniden bağlanır
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> liste = aboneler.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        liste.add(emitter);

        Runnable temizle = () -> {
            liste.remove(emitter);
            if (liste.isEmpty()) aboneler.remove(conversationId, liste);
        };
        emitter.onCompletion(temizle);
        emitter.onTimeout(temizle);
        emitter.onError(e -> temizle.run());

        try {
            // İlk olay: bağlantının kurulduğunu tarayıcıya bildirir
            emitter.send(SseEmitter.event().name("acildi").data("ok"));
        } catch (Exception ex) {
            temizle.run();
        }
        return emitter;
    }

    /** Sohbete yeni mesaj geldiğini bildirir; içerik taşımaz, istemci listeyi tazeler. */
    public void publish(Long conversationId) {
        List<SseEmitter> liste = aboneler.get(conversationId);
        if (liste == null || liste.isEmpty()) return;
        List<SseEmitter> kopmus = new ArrayList<>();
        for (SseEmitter e : liste) {
            try {
                e.send(SseEmitter.event().name("yeni").data("1"));
            } catch (Exception ex) {
                // Kopmuş bağlantı sadece listeden temizlenir; completeWithError çağrılmaz
                // çünkü zaten kapanmış AsyncContext üzerinde Tomcat hata fırlatır.
                log.debug("SSE gönderilemedi, abone listeden temizleniyor: {}", ex.getMessage());
                kopmus.add(e);
            }
        }
        if (!kopmus.isEmpty()) {
            liste.removeAll(kopmus);
            if (liste.isEmpty()) aboneler.remove(conversationId, liste);
        }
    }

    /**
     * Caddy/ters vekil ve tarayıcıların boşta kalan bağlantıyı koparmasını önlemek
     * için 20 saniyede bir SSE kalp atışı (ping yorumu) gönderir.
     */
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

    /** Testler ve tanılama için açık bağlantı sayısı. */
    public int subscriberCount(Long conversationId) {
        List<SseEmitter> liste = aboneler.get(conversationId);
        return liste == null ? 0 : liste.size();
    }
}

