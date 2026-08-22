package app.kitapla.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Başarısız giriş denemelerini kayan pencerede sayar; kaba kuvvet denemelerini yavaşlatır.
 * Bellek içi tutulur — tek örnekli yerel kurulum için yeterli, uygulama yeniden başlarsa sıfırlanır.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${kitapla.login.max-attempts:8}") int maxAttempts,
                               @Value("${kitapla.login.window-minutes:15}") int windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** Aynı e-posta + IP çifti ayrı sayılır; tek kullanıcı tüm ağı kilitlemesin. */
    public static String key(String email, String ip) {
        return (email == null ? "?" : email.trim().toLowerCase()) + "|" + (ip == null ? "?" : ip);
    }

    public boolean isBlocked(String key) {
        return recent(key).size() >= maxAttempts;
    }

    public void recordFailure(String key) {
        Deque<Instant> hits = recent(key);
        hits.addLast(Instant.now());
        failures.put(key, hits);
    }

    /** Başarılı girişte sayaç temizlenir. */
    public void reset(String key) {
        failures.remove(key);
    }

    public int windowMinutes() {
        return (int) window.toMinutes();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** Pencere dışında kalan denemeleri atarak güncel listeyi verir. */
    private Deque<Instant> recent(String key) {
        Deque<Instant> hits = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            Instant limit = Instant.now().minus(window);
            while (!hits.isEmpty() && hits.peekFirst().isBefore(limit)) hits.pollFirst();
        }
        return hits;
    }
}
