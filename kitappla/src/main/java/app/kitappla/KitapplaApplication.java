package app.kitappla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class KitapplaApplication {
    public static void main(String[] args) {
        SpringApplication.run(KitapplaApplication.class, args);
    }
}
