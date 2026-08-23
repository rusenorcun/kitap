package app.kitapla.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * E-posta gönderimi. Sağlayıcıdan bağımsızdır: standart {@code spring.mail.*}
 * ayarlarını kullanır, dolayısıyla Resend, Brevo, Gmail, Yandex ya da kendi
 * Postfix sunucun fark etmeksizin aynı kodla çalışır.
 * <p>
 * {@code kitapla.mail.enabled=false} iken (yerel geliştirmenin varsayılanı)
 * hiçbir şey gönderilmez; ileti loglanır ve son 50 tanesi bellekte tutulur.
 * Böylece SMTP kurmadan tüm akışlar denenebilir.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int OUTBOX_LIMIT = 50;

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final boolean enabled;
    private final String from;
    private final String fromName;
    private final String baseUrl;

    /** Gönderim kapalıyken son iletiler burada tutulur (yerel deneme ve testler için). */
    private final Deque<MailMessage> outbox = new ConcurrentLinkedDeque<>();

    public MailService(JavaMailSender sender, TemplateEngine templates,
                       @Value("${kitapla.mail.enabled:false}") boolean enabled,
                       @Value("${kitapla.mail.from:kitapla@localhost}") String from,
                       @Value("${kitapla.mail.from-name:KİTAPLA}") String fromName,
                       @Value("${kitapla.base-url:http://localhost:8080}") String baseUrl) {
        this.sender = sender;
        this.templates = templates;
        this.enabled = enabled;
        this.from = from;
        this.fromName = fromName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Şablonu işleyip gönderir. Gönderim hatası çağıranın akışını bozmaz:
     * kullanıcı işlemi (kayıt, şifre sıfırlama talebi) e-posta gitmese de tamamlanır.
     */
    public void send(String to, String subject, String template, Map<String, Object> model) {
        String html;
        try {
            Context ctx = new Context();
            ctx.setVariables(model);
            ctx.setVariable("baseUrl", baseUrl);
            html = templates.process("mail/" + template, ctx);
        } catch (Exception ex) {
            log.error("E-posta şablonu işlenemedi ({}): {}", template, ex.getMessage());
            return;
        }

        remember(new MailMessage(to, subject, html));

        if (!enabled) {
            log.info("[POSTA KAPALI] Gönderilecekti -> {} | {}", to, subject);
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("E-posta gönderildi -> {} | {}", to, subject);
        } catch (Exception ex) {
            log.error("E-posta gönderilemedi -> {} | {} : {}", to, subject, ex.getMessage());
        }
    }

    private void remember(MailMessage m) {
        outbox.addLast(m);
        while (outbox.size() > OUTBOX_LIMIT) outbox.pollFirst();
    }

    /** Son iletiler (en yeni sonda). Testler ve yerel doğrulama için. */
    public List<MailMessage> outbox() {
        return List.copyOf(outbox);
    }

    public void clearOutbox() {
        outbox.clear();
    }
}
