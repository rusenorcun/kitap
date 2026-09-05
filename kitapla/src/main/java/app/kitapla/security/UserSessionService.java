package app.kitapla.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kullanıcı oturumlarını SessionRegistry üzerinden yönetir ve şifre sıfırlama/değiştirme
 * durumlarında açık oturumları geçersiz kılar.
 */
@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final SessionRegistry sessionRegistry;

    public UserSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Verilen kullanıcının tüm açık oturumlarını sonlandırır.
     */
    public void expireUserSessions(Long userId) {
        if (userId == null) return;
        int count = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserDetails details && details.getUser() != null) {
                if (userId.equals(details.getUser().getId())) {
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                    for (SessionInformation session : sessions) {
                        session.expireNow();
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            log.info("Kullanıcı (id={}) için {} açık oturum sonlandırıldı.", userId, count);
        }
    }

    /**
     * E-posta adresine göre oturumları sonlandırır.
     */
    public void expireUserSessions(String email) {
        if (email == null || email.isBlank()) return;
        String normalized = email.trim();
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserDetails details && details.getUser() != null) {
                if (normalized.equalsIgnoreCase(details.getUsername())) {
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                    for (SessionInformation session : sessions) {
                        session.expireNow();
                    }
                }
            }
        }
    }
}
