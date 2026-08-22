package app.kitapla.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sınırı aşan giriş denemelerini kimlik doğrulamaya hiç ulaştırmadan geri çevirir.
 * Böylece şifre karşılaştırması (bcrypt) da boşuna çalıştırılmaz.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /** servletPath yerine matcher: farklı sunucu/test ortamlarında da aynı şekilde eşleşir. */
    private static final RequestMatcher LOGIN_POST = new AntPathRequestMatcher("/login", "POST");

    private final LoginAttemptService attempts;

    public LoginRateLimitFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (LOGIN_POST.matches(request)) {
            String key = LoginAttemptService.key(request.getParameter("email"), request.getRemoteAddr());
            if (attempts.isBlocked(key)) {
                response.sendRedirect(request.getContextPath() + "/login?kilit");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
