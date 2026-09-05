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
            String email = request.getParameter("email");
            String ip = request.getRemoteAddr();
            attempts.recordFailure(email, ip);
            // Bu deneme sınırı doldurduysa kullanıcıya doğrudan kilit mesajı göster
            String target = attempts.isBlocked(email, ip) ? "/login?kilit" : "/login?error";
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    public static AuthenticationSuccessHandler success(LoginAttemptService attempts) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication auth) -> {
            String email = request.getParameter("email");
            String ip = request.getRemoteAddr();
            attempts.reset(email, ip);
            redirect(request, response, "/panom");
        };
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }
}
