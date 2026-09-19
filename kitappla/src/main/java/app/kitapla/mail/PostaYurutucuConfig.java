package app.kitapla.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * E-postaların SMTP teslimi için ayrı iş parçacığı havuzu. Teslim, sağlayıcıya göre birkaç
 * saniye sürebildiği için (TLS + kimlik doğrulama + gönderim) kullanıcı isteğini bekletmez.
 * <p>
 * Havuz küçük tutulur: sağlayıcıların saatlik sınırı ve eşzamanlı bağlantı kısıtı vardır.
 * Kuyruk dolarsa görev çağıranın iş parçacığında çalışır (ileti kaybolmaz, yalnızca o istek
 * yavaşlar). Kapanışta kuyruktaki iletilerin bitmesi beklenir.
 * {@code kitapla.mail.async=false} (testler) teslimi aynı iş parçacığında yapar.
 */
@Configuration
public class PostaYurutucuConfig {

    public static final String BEAN = "postaYurutucu";

    @Bean(name = BEAN)
    public TaskExecutor postaYurutucu(@Value("${kitapla.mail.async:true}") boolean async) {
        if (!async) return new SyncTaskExecutor();
        ThreadPoolTaskExecutor y = new ThreadPoolTaskExecutor();
        y.setThreadNamePrefix("posta-");
        y.setCorePoolSize(2);
        y.setMaxPoolSize(2);
        y.setQueueCapacity(500);
        y.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        y.setWaitForTasksToCompleteOnShutdown(true);
        y.setAwaitTerminationSeconds(30);
        y.initialize();
        return y;
    }
}
