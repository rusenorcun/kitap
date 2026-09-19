package app.kitapla.security;

import app.kitapla.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Oturumdaki kullanıcıya erişim için küçük yardımcı. */
public final class CurrentUser {

    private CurrentUser() {}

    /** Giriş yapılmamışsa null döner. */
    public static User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AppUserDetails details) return details.getUser();
        return null;
    }

    public static boolean isLoggedIn() {
        return get() != null;
    }
}
