package app.kitapla.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/** Giriş sonuçlarını deneme sayacına işleyen handler'lar. */
public final class LoginAttemptHandlers {

    private LoginAttemptHandlers() {}

    public static AuthenticationFailureHandler failure(LoginAttemptService attempts) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) -> {
            String key = LoginAttemptService.key(request.getParameter("email"), request.getRemoteAddr());
            attempts.recordFailure(key);
            // Bu deneme sınırı doldurduysa kullanıcıya doğrudan kilit mesajı göster
            String target = attempts.isBlocked(key) ? "/login?kilit" : "/login?error";
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    public static AuthenticationSuccessHandler success(LoginAttemptService attempts) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication auth) -> {
            attempts.reset(LoginAttemptService.key(request.getParameter("email"), request.getRemoteAddr()));
            redirect(request, response, "/panom");
        };
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }
}
