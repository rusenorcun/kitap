package app.kitapla.security;

import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Oturumdaki kullanıcıyı her istekte veritabanından tazeler.
 * <p>
 * Spring Security yetkileri giriş anında dondurur; bu yüzden yönetici bir üyeyi
 * onayladığında, yetkilendirdiğinde ya da askıya aldığında değişiklik ancak yeniden
 * giriş yapılınca görünür olurdu. Bu filtre kararı her istekte güncel satıra göre
 * verdirir: askıya alınan ya da silinen hesabın açık oturumu da anında düşer.
 */
public class FreshPrincipalFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public FreshPrincipalFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails details) {
            User fresh = users.findById(details.getUser().getId()).orElse(null);

            if (fresh == null || fresh.isBlocked()) {
                dropSession(request);
            } else {
                AppUserDetails refreshed = new AppUserDetails(fresh);
                var token = new UsernamePasswordAuthenticationToken(
                        refreshed, auth.getCredentials(), refreshed.getAuthorities());
                token.setDetails(auth.getDetails());
                SecurityContextHolder.getContext().setAuthentication(token);
            }
        }

        chain.doFilter(request, response);
    }

    /** Oturumu tamamen düşürür; yalnızca bağlamı temizlemek yetmez (oturumdan geri yüklenir). */
    private void dropSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
