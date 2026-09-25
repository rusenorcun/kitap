package app.kitappla.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

import java.util.Set;

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

    /**
     * Başarılı girişte, giriş sayfasına yönlendirilmeden önce açılmak istenen sayfaya döner
     * (ör. e-postadaki okul adresi doğrulama bağlantısı); yoksa panoya gider. Hangi isteklerin
     * kaydedileceğini {@link #sayfaIstekleri()} belirler.
     */
    public static AuthenticationSuccessHandler success(LoginAttemptService attempts, RequestCache cache) {
        // Hatırlanan sayfa yoksa: yönetim alan adında panoya, sitede üyenin panosuna
        SavedRequestAwareAuthenticationSuccessHandler hedef = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
                if (Boolean.TRUE.equals(request.getAttribute(app.kitappla.web.YonetimAlanAdiFiltresi.YONETIM_ALANI)))
                    return "/admin";
                return super.determineTargetUrl(request, response);
            }
        };
        hedef.setDefaultTargetUrl("/panom");
        hedef.setRequestCache(cache);
        return (HttpServletRequest request, HttpServletResponse response, Authentication auth) -> {
            String email = request.getParameter("email");
            String ip = request.getRemoteAddr();
            attempts.reset(email, ip);
            hedef.onAuthenticationSuccess(request, response, auth);
        };
    }

    /**
     * Yalnızca tarayıcının doğrudan açtığı sayfalar hatırlanır. Canlı akış (EventSource),
     * HTMX parçası ya da fetch isteği kaydedilseydi, girişten sonra kullanıcı bir HTML
     * parçasına veya olay akışına yönlendirilirdi.
     */
    public static RequestCache sayfaIstekleri() {
        MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(
                new AntPathRequestMatcher("/**", "GET"),
                html,
                new NegatedRequestMatcher(new RequestHeaderRequestMatcher("HX-Request")),
                new NegatedRequestMatcher(new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"))));
        return cache;
    }
}
