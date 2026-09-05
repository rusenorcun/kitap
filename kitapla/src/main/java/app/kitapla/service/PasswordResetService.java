package app.kitapla.service;

import app.kitapla.domain.AuthToken;
import app.kitapla.domain.TokenType;
import app.kitapla.domain.User;
import app.kitapla.mail.MailService;
import app.kitapla.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Şifremi unuttum akışı. */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final TokenService tokens;
    private final MailService mail;
    private final PasswordEncoder encoder;
    private final app.kitapla.security.UserSessionService userSessions;

    public PasswordResetService(UserRepository users, TokenService tokens, MailService mail,
                                PasswordEncoder encoder,
                                app.kitapla.security.UserSessionService userSessions) {
        this.users = users;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.userSessions = userSessions;
    }

    /**
     * Sıfırlama bağlantısı gönderir.
     * <p>
     * Adres kayıtlı değilse sessizce hiçbir şey yapılmaz — çağıran her durumda
     * aynı mesajı gösterir; böylece bu uç, hangi e-postaların kayıtlı olduğunu
     * sızdıran bir hesap sayacı hâline gelmez.
     */
    @Transactional
    public void request(String email) {
        String normalized = UserService.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) return;

        users.findByEmail(normalized).ifPresentOrElse(user -> {
            if (user.isBlocked()) {
                log.info("Askıdaki hesap için şifre sıfırlama istendi: {}", normalized);
                return;
            }
            String token = tokens.issue(user, TokenType.PASSWORD_RESET);
            mail.send(user.getEmail(), "KİTAPLA — Şifre sıfırlama", "sifre-sifirlama",
                    Map.of("ad", user.getName(),
                           "link", mail.baseUrl() + "/sifre-sifirla?token=" + token));
        }, () -> log.info("Kayıtsız adres için şifre sıfırlama istendi: {}", normalized));
    }

    /** Bağlantıdaki jetonun hâlâ geçerli olup olmadığı (form gösterilmeden önce). */
    public boolean isValid(String token) {
        return tokens.verify(token, TokenType.PASSWORD_RESET).isPresent();
    }

    /** Jetonu harcayıp yeni şifreyi yazar. */
    @Transactional
    public void reset(String token, String newPassword, String confirmPassword) {
        AuthToken t = tokens.verify(token, TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalStateException(
                        "Bağlantı geçersiz ya da süresi dolmuş. Yeniden sıfırlama isteyebilirsin."));

        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalStateException("Yeni şifre en az 6 karakter olmalı.");
        if (!newPassword.equals(confirmPassword))
            throw new IllegalStateException("Şifreler birbiriyle eşleşmiyor.");

        User user = t.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        tokens.consume(t);
        userSessions.expireUserSessions(user.getId());

        mail.send(user.getEmail(), "KİTAPLA — Şifren değiştirildi", "sifre-degisti",
                Map.of("ad", user.getName()));
    }
}
