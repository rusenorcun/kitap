package app.kitapla.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Uygulama içi önbellek.
 *
 * <p><b>Nasıl çalışır:</b> Değerler JVM belleğinde Caffeine ile tutulur. Her önbelleğin
 * bir kayıt sınırı (dolunca en az kullanılanlar atılır) ve bir yaşam süresi vardır.</p>
 *
 * <p><b>Tazelik:</b> Veriyi değiştiren servis ilgili kaydı açıkça siler. Silme, işlem
 * commit olduktan sonra uygulanır ({@link TransactionAwareCacheManagerProxy}); commit'ten
 * önce silinseydi, arada okuyan bir istek eski veriyi yeniden önbelleğe yazabilirdi.
 * Yaşam süresi yalnızca güvenlik ağıdır: kaçan bir yarış en fazla bu süre kadar görünür.</p>
 *
 * <p>Tek sunucu için tasarlanmıştır. Uygulama birden fazla kopya çalışırsa paylaşımlı bir
 * önbelleğe (Redis vb.) geçilmelidir.</p>
 */
@Configuration
@EnableCaching
public class OnbellekConfig {

    /** Kullanıcı başına okunmamış bildirim sayısı (nav rozeti). Anahtar: kullanıcı kimliği. */
    public static final String OKUNMAMIS_BILDIRIM = "okunmamisBildirim";

    /** Kullanıcı başına okunmamış mesajı olan sohbet sayısı (nav rozeti). Anahtar: kullanıcı kimliği. */
    public static final String OKUNMAMIS_SOHBET = "okunmamisSohbet";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                onbellek(OKUNMAMIS_BILDIRIM, Duration.ofMinutes(1), 50_000),
                onbellek(OKUNMAMIS_SOHBET, Duration.ofMinutes(1), 50_000)
        ));
        manager.initializeCaches();
        return new TransactionAwareCacheManagerProxy(manager);
    }

    private static CaffeineCache onbellek(String ad, Duration yasamSuresi, long enFazlaKayit) {
        return new CaffeineCache(ad, Caffeine.newBuilder()
                .expireAfterWrite(yasamSuresi)
                .maximumSize(enFazlaKayit)
                .build());
    }
}
