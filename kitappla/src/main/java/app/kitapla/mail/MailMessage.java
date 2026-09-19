package app.kitapla.mail;

import java.time.Instant;

/** Gönderilen (ya da gönderilmek üzere kaydedilen) bir e-posta. */
public record MailMessage(String to, String subject, String html, Instant at) {
    public MailMessage(String to, String subject, String html) {
        this(to, subject, html, Instant.now());
    }
}
