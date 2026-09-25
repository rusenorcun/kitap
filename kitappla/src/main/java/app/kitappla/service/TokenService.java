package app.kitappla.service;

import app.kitappla.domain.AuthToken;
import app.kitappla.domain.TokenType;
import app.kitappla.domain.User;
import app.kitappla.repo.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Tek kullanımlık bağlantı jetonları üretir ve doğrular. */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL64 = Base64.getUrlEncoder().withoutPadding();

    private final AuthTokenRepository tokens;
    private final Duration resetTtl;
    private final Duration verifyTtl;
    private final int maxPerHour;

    public TokenService(AuthTokenRepository tokens,
                        @Value("${kitappla.token.reset-minutes:60}") int resetMinutes,
                        @Value("${kitappla.token.verify-hours:48}") int verifyHours,
                        @Value("${kitappla.token.max-per-hour:5}") int maxPerHour) {
        this.tokens = tokens;
        this.resetTtl = Duration.ofMinutes(resetMinutes);
        this.verifyTtl = Duration.ofHours(verifyHours);
        this.maxPerHour = maxPerHour;
    }

    /**
     * Saatlik jeton sınırı dolmuş mu? Çağıran, istisna üretmeden önden sorabilsin diye ayrıdır:
     * {@link #issue} içinden fırlayan hata, saran işlemi geri alınacak diye işaretler ve
     * hatayı yutan çağıran bile commit edemez.
     */
    public boolean limitDoldu(User user, TokenType type) {
        return tokens.countByUserAndTypeAndCreatedAtAfter(user, type, Instant.now().minus(Duration.ofHours(1)))
                >= maxPerHour;
    }

    /** Bağlantıda görünen ham jetonu döndürür; veritabanına yalnızca özeti yazılır. */
    @Transactional
    public String issue(User user, TokenType type) {
        if (limitDoldu(user, type)) {
            throw new IllegalStateException("Çok fazla istek gönderildi. Lütfen bir süre sonra tekrar dene.");
        }

        invalidate(user, type);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = URL64.encodeToString(raw);

        AuthToken t = new AuthToken();
        t.setUser(user);
        t.setType(type);
        if (type == TokenType.EMAIL_VERIFY) t.setVerificationEmail(user.getStudentEmail());
        if (type == TokenType.ACCOUNT_VERIFY) t.setVerificationEmail(user.getEmail());
        t.setTokenHash(hash(token));
        t.setExpiresAt(Instant.now().plus(type == TokenType.PASSWORD_RESET ? resetTtl : verifyTtl));
        tokens.save(t);

        return token;
    }

    /** Geçerli ve kullanılmamışsa jetonu verir; aksi halde boş döner. */
    public Optional<AuthToken> verify(String token, TokenType type) {
        if (token == null || token.isBlank()) return Optional.empty();
        return tokens.findByTokenHashWithUser(hash(token))
                .filter(t -> t.getType() == type)
                .filter(AuthToken::isUsable);
    }

    /**
     * Jetonu geçerliliğine bakmadan bulur. Yalnızca "bu bağlantı zaten kullanıldı" gibi bilgi
     * mesajları için; yetki kararı her zaman {@link #verify} ile verilir.
     */
    public Optional<AuthToken> find(String token, TokenType type) {
        if (token == null || token.isBlank()) return Optional.empty();
        return tokens.findByTokenHashWithUser(hash(token)).filter(t -> t.getType() == type);
    }

    /** Jetonu harcar; ikinci kez kullanılamaz. */
    @Transactional
    public void consume(AuthToken token) {
        token.setUsedAt(Instant.now());
        tokens.save(token);
    }

    @Transactional
    public void invalidate(User user, TokenType type) {
        var pending = tokens.findByUserAndTypeAndUsedAtIsNull(user, type);
        pending.forEach(t -> t.setUsedAt(Instant.now()));
        tokens.saveAll(pending);
    }

    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Jeton özeti hesaplanamadı", ex);
        }
    }
}
