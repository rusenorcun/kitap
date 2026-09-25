package app.kitappla.config;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${kitappla.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Kapak görselleri herkese açık sunulur. Öğrenci belgeleri BURADA sunulmaz;
        // yalnızca admin ucundan (AdminController) erişilir.
        Path covers = Path.of(uploadDir, "covers").toAbsolutePath().normalize();
        String location = covers.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/covers/**")
                .addResourceLocations(location)
                // Dosya adları rastgele (UUID) ve bir kez yazılır: tarayıcı ve vekil uzun süre saklayabilir
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

}
