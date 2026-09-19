package app.kitapla.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * OWASP ve RFC 6265bis standartlarına uygun kalıcı oturum (Remember-Me) servisi.
 * <p>
 * {@link PersistentTokenBasedRememberMeServices} üzerine:
 * <ul>
 *   <li>{@code SameSite=Lax} özniteliği,</li>
 *   <li>{@code HttpOnly=true} güvenliği,</li>
 *   <li>HTTPS/üretim durumunda {@code Secure=true} bayrağı,</li>
 *   <li>Oturumsuz (yalnızca çerezle) yapılan çıkışlarda da DB token temizliği</li>
 * </ul>
 * ekleyerek çerez kopyalama ve CSRF risklerini asgariye indirir.
 */
public class KitapplaRememberMeServices extends PersistentTokenBasedRememberMeServices {

    public static final String REMEMBER_ME_COOKIE_NAME = "kitappla-remember-me";
    public static final String REMEMBER_ME_PARAM = "remember-me";

    private final PersistentTokenRepository tokenRepository;
    private Boolean secureCookie;

    public KitapplaRememberMeServices(String key, UserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
        setParameter(REMEMBER_ME_PARAM);
        setCookieName(REMEMBER_ME_COOKIE_NAME);
    }

    @Override
    public void setUseSecureCookie(boolean useSecureCookie) {
        super.setUseSecureCookie(useSecureCookie);
        this.secureCookie = useSecureCookie;
    }

    @Override
    protected void setCookie(String[] tokens, int maxAge, HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = encodeCookie(tokens);
        Cookie cookie = new Cookie(getCookieName(), cookieValue);
        cookie.setPath(getCookiePath(request));
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        boolean secure = (secureCookie != null) ? secureCookie : request.isSecure();
        if (secure) {
            cookie.setSecure(true);
        }
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    @Override
    protected void cancelCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(getCookieName(), null);
        cookie.setMaxAge(0);
        cookie.setPath(getCookiePath(request));
        cookie.setHttpOnly(true);
        boolean secure = (secureCookie != null) ? secureCookie : request.isSecure();
        if (secure) {
            cookie.setSecure(true);
        }
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        super.logout(request, response, authentication);
        if (authentication == null && tokenRepository != null) {
            String rememberMeCookie = extractRememberMeCookie(request);
            if (rememberMeCookie != null && !rememberMeCookie.isBlank()) {
                try {
                    String[] cookieTokens = decodeCookie(rememberMeCookie);
                    if (cookieTokens.length == 2) {
                        String series = cookieTokens[0];
                        PersistentRememberMeToken token = tokenRepository.getTokenForSeries(series);
                        if (token != null) {
                            tokenRepository.removeUserTokens(token.getUsername());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String getCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty()) ? contextPath : "/";
    }
}
