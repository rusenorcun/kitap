package app.kitapla.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${kitapla.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Kapak görselleri herkese açık sunulur. Öğrenci belgeleri BURADA sunulmaz;
        // yalnızca admin ucundan (AdminController) erişilir.
        Path covers = Path.of(uploadDir, "covers").toAbsolutePath();
        registry.addResourceHandler("/uploads/covers/**")
                .addResourceLocations("file:" + covers + "/");
    }
}
