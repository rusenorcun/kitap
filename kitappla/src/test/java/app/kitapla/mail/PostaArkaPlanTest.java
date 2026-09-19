package app.kitapla.mail;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.service.PasswordResetService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SMTP teslimi yavaş olsa da (Natro'da birkaç saniye) kullanıcı isteği beklememeli:
 * şifre sıfırlama talebi hemen döner, posta arka planda yine de teslim edilir.
 */
@SpringBootTest(properties = {"kitapla.mail.enabled=true", "kitapla.mail.async=true"})
@ActiveProfiles("test")
class PostaArkaPlanTest {

    @Autowired PasswordResetService passwordReset;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockBean JavaMailSender sender;

    @Test
    void yavasSmtpIstegiBekletmezVePostaYineDeGider() throws Exception {
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> { Thread.sleep(3000); return null; }).when(sender).send(any(MimeMessage.class));

        User u = new User();
        u.setName("Arka Plan");
        u.setEmail("arkaplan-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        users.save(u);

        long bas = System.nanoTime();
        passwordReset.request(u.getEmail());
        long sureMs = (System.nanoTime() - bas) / 1_000_000;

        assertThat(sureMs).as("istek SMTP'yi beklememeli").isLessThan(1500);
        verify(sender, timeout(10_000)).send(any(MimeMessage.class));
    }
}
