package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Şifre sıfırlama ve e-posta doğrulama için tek kullanımlık bağlantı jetonu.
 * <p>
 * Jetonun kendisi veritabanında saklanmaz; yalnızca SHA-256 özeti tutulur.
 * Böylece veritabanı okunsa bile geçerli bir bağlantı üretilemez.
 */
@Entity
@Table(name = "auth_tokens", indexes = @Index(name = "ix_auth_token_hash", columnList = "tokenHash"))
@Getter
@Setter
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType type;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}
