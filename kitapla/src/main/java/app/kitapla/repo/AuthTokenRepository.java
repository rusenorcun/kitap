package app.kitapla.repo;

import app.kitapla.domain.AuthToken;
import app.kitapla.domain.TokenType;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /** Şablonlar/servisler kullanıcıya eriştiği için birlikte çekilir (open-in-view kapalı). */
    @Query("select t from AuthToken t join fetch t.user where t.tokenHash = :hash")
    Optional<AuthToken> findByTokenHashWithUser(@Param("hash") String hash);

    List<AuthToken> findByUserAndTypeAndUsedAtIsNull(User user, TokenType type);

    long countByUserAndTypeAndCreatedAtAfter(User user, TokenType type, Instant after);

    void deleteByUser(User user);
}
