package app.kitappla.service;

import app.kitappla.domain.AuthToken;
import app.kitappla.domain.TokenType;
import app.kitappla.domain.User;
import app.kitappla.mail.MailService;
import app.kitappla.repo.UserRepository;
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
    private final app.kitappla.security.UserSessionService userSessions;
    private final app.kitappla.config.Marka marka;

    public PasswordResetService(UserRepository users, TokenService tokens, MailService mail,
                                PasswordEncoder encoder,
                                app.kitappla.security.UserSessionService userSessions,
                                app.kitappla.config.Marka marka) {
        this.users = users;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.userSessions = userSessions;
        this.marka = marka;
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
        request(email, false);
    }

    /**
     * @param mobil istek mobil uygulamadan geldiyse true: bağlantı uygulamanın App Link yolundadır
     *              ({@code /uygulamada-ac/sifre-sifirla}); uygulama yüklüyse tarayıcıya uğramadan
     *              şifre sıfırlama ekranı açılır
     */
    @Transactional
    public void request(String email, boolean mobil) {
        String normalized = UserService.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) return;

        users.findByEmail(normalized).ifPresentOrElse(user -> {
            if (user.isBlocked()) {
                log.info("Askıdaki hesap için şifre sıfırlama istendi: {}", normalized);
                return;
            }
            // Jeton sınırı aşıldığında hata dışarı sızarsa, kayıtlı adres "çok fazla istek",
            // kayıtsız adres jenerik cevap alırdı; bu fark tek başına hesap sayacı olur.
            // Sınır yine uygulanır, yalnızca sonucu çağırana bildirilmez.
            if (tokens.limitDoldu(user, TokenType.PASSWORD_RESET)) {
                log.info("Şifre sıfırlama istek sınırı aşıldı: {}", normalized);
                return;
            }
            String token = tokens.issue(user, TokenType.PASSWORD_RESET);
            mail.send(user.getEmail(), "Şifre sıfırlama", "sifre-sifirlama",
                    Map.of("ad", user.getName(),
                           "link", mail.baseUrl() + (mobil ? "/uygulamada-ac/sifre-sifirla?token=" : "/sifre-sifirla?token=") + token));
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
        // Profildeki şifre değiştirmeyle aynı kural. Jeton henüz harcanmadığı için aynı
        // bağlantıyla farklı bir şifre denenebilir.
        if (encoder.matches(newPassword, user.getPasswordHash()))
            throw new IllegalStateException("Yeni şifre eskisiyle aynı olamaz.");
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        tokens.consume(t);
        userSessions.expireUserSessions(user.getId());

        mail.send(user.getEmail(), "Şifren değiştirildi", "sifre-degisti",
                Map.of("ad", user.getName()));
    }
}
