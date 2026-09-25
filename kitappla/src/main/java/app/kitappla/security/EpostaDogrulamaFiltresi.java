package app.kitappla.security;

import app.kitappla.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * E-postasını doğrulamamış üyenin işlem yapmasını engeller: oturum açabilir ve sayfaları
 * gezebilir, ama bağış, istek, takas, mesaj gibi durum değiştiren istekleri (GET/HEAD/OPTIONS
 * dışındakiler) reddedilir. Hesabın kendisini ilgilendiren birkaç uç (çıkış, profil, şifre,
 * bildirim okundu, doğrulamayı yeniden gönderme) açık kalır.
 * <p>
 * {@link FreshPrincipalFilter}'dan sonra çalışır; doğrulama bağlantısı tıklandığında karar
 * yeniden giriş beklemeden değişir. Yöneticiler kapsam dışıdır.
 * <ul>
 *   <li>API: 403 ve {@code {"error": "...", "code": "EMAIL_NOT_VERIFIED"}}</li>
 *   <li>Web: {@code /hesap-dogrulama} sayfasına yönlendirme (HTMX isteğinde {@code HX-Redirect})</li>
 * </ul>
 */
public class EpostaDogrulamaFiltresi extends OncePerRequestFilter {

    public static final String KOD = "EMAIL_NOT_VERIFIED";
    public static final String MESAJ =
            "Bu işlem için önce e-posta adresini doğrulamalısın. Gelen kutundaki bağlantıya tıkla.";
    public static final String SAYFA = "/hesap-dogrulama";

    private static final Set<String> GUVENLI = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Tam eşleşen açık uçlar. */
    private static final Set<String> ACIK = Set.of(
            "/logout", "/login", "/register", "/sifremi-unuttum", "/sifre-sifirla",
            "/profil", "/profil/sifre", SAYFA + "/yeniden",
            "/api/v1/me", "/api/v1/me/password", "/api/v1/me/email-verification");

    /** Önekiyle açık uçlar. */
    private static final String[] ACIK_ONEK = { "/bildirimler/", "/api/v1/auth/", "/api/v1/notifications/" };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (GUVENLI.contains(request.getMethod())) return true;
        String yol = request.getRequestURI().substring(request.getContextPath().length());
        if (ACIK.contains(yol)) return true;
        for (String onek : ACIK_ONEK) if (yol.startsWith(onek)) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        User user = CurrentUser.get();
        if (user == null || user.isEmailVerified() || user.isAdmin()) {
            chain.doFilter(request, response);
            return;
        }

        String yol = request.getRequestURI().substring(request.getContextPath().length());
        if (yol.startsWith("/api/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"" + MESAJ + "\",\"message\":\"" + MESAJ + "\",\"code\":\"" + KOD + "\"}");
        } else if ("true".equals(request.getHeader("HX-Request"))) {
            response.setHeader("HX-Redirect", request.getContextPath() + SAYFA);
            response.setStatus(HttpStatus.NO_CONTENT.value());
        } else {
            response.sendRedirect(request.getContextPath() + SAYFA);
        }
    }
}
