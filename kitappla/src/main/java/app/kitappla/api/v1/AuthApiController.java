package app.kitappla.api.v1;

import app.kitappla.api.dto.*;
import app.kitappla.domain.School;
import app.kitappla.domain.User;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.security.LoginAttemptService;
import app.kitappla.service.PasswordResetService;
import app.kitappla.service.Quota;
import app.kitappla.service.QuotaService;
import app.kitappla.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final UserRepository users;
    private final UserService userService;
    private final QuotaService quotaService;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthApiController(UserRepository users,
                             UserService userService,
                             QuotaService quotaService,
                             PasswordResetService passwordResetService,
                             PasswordEncoder passwordEncoder,
                              LoginAttemptService loginAttempts,
                              CsrfTokenRepository apiCsrfTokenRepository,
                              SessionRegistry sessionRegistry) {
        this.users = users;
        this.userService = userService;
        this.quotaService = quotaService;
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.csrfAuthenticationStrategy = new CsrfAuthenticationStrategy(apiCsrfTokenRepository);
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/login")
    public ResponseEntity<MeDto> login(@Valid @RequestBody LoginBody body,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        String email = UserService.normalizeEmail(body.email());
        String ip = request.getRemoteAddr();

        if (loginAttempts.isBlocked(email, ip)) {
            throw new IllegalStateException("Çok fazla hatalı deneme. Hesabın 15 dakika kilitlendi.");
        }

        User user = users.findByEmail(email).orElse(null);
        // Kayıtsız adreste de bcrypt çalıştırılır: aksi hâlde yanıt süresindeki fark
        // (≈100 ms) hangi adreslerin kayıtlı olduğunu ele verir. Web girişinde bunu
        // Spring Security'nin DaoAuthenticationProvider'ı yapıyor.
        String hash = user == null ? sahteHash() : user.getPasswordHash();
        boolean dogru = passwordEncoder.matches(body.password(), hash);
        if (user == null || !dogru) {
            loginAttempts.recordFailure(email, ip);
            throw new IllegalStateException("E-posta ya da şifre hatalı.");
        }

        if (user.isBlocked()) {
            throw new IllegalStateException("Hesabın askıya alınmış.");
        }

        loginAttempts.reset(email, ip);

        setAuthenticatedUser(user, request, response);

        Quota quota = quotaService.quotaFor(user);
        return ResponseEntity.ok(ApiDtoMapper.toMeDto(user, quota));
    }

    @PostMapping("/register")
    public ResponseEntity<MeDto> register(@Valid @RequestBody RegisterBody body,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        School school = School.of(body.school());

        User user = userService.register(
                body.name(),
                body.email(),
                body.password(),
                body.address(),
                body.phone(),
                school,
                false,
                null,
                null,
                null,
                true
        );

        // Doğrulanmamış hesapla da oturum açılır; işlem uçları EMAIL_NOT_VERIFIED ile 403 döner.
        setAuthenticatedUser(user, request, response);

        Quota quota = quotaService.quotaFor(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Student-Verification-Sent", Boolean.toString(user.isStudentVerificationSent()))
                .header("X-Email-Verification-Sent", Boolean.toString(user.isEmailVerificationSent()))
                .body(ApiDtoMapper.toMeDto(user, quota));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageDto> forgotPassword(@Valid @RequestBody ForgotPasswordBody body) {
        passwordResetService.request(body.email(), true);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageDto("Şifre sıfırlama bağlantısı e-posta adresine gönderildi."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordBody body) {
        passwordResetService.reset(body.token(), body.newPassword(), body.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Doğrulama bağlantısı uygulamada açıldığında (App Link: {@code /uygulamada-ac/eposta-dogrula}) onayı
     * uygulama bu uçla yapar. Web sayfası gibi oturum gerektirmez; geçersiz jeton hata değil, sonuçtur.
     */
    @PostMapping("/verify-email")
    public EmailVerificationDto verifyEmail(@Valid @RequestBody VerifyEmailBody body) {
        return new EmailVerificationDto(userService.confirmAccountEmail(body.token()).name());
    }

    private volatile String sahteHash;

    /** Zamanlama eşitlemesi için bir kez üretilen, hiçbir şifreyle eşleşmeyen özet. */
    private String sahteHash() {
        String h = sahteHash;
        if (h == null) {
            h = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
            sahteHash = h;
        }
        return h;
    }

    private void setAuthenticatedUser(User user, HttpServletRequest request, HttpServletResponse response) {
        // Oturum sabitleme (session fixation) korumasi: kimlik dogrulandiktan sonra
        // oturum kimligi yenilenir; giris oncesi elde edilmis bir JSESSIONID
        // dogrulanmis oturuma donusemez. Web zincirinde bunu Spring Security yapar,
        // burada el ile oturum acildigi icin acikca cagirmak gerekir.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        AppUserDetails details = new AppUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        csrfAuthenticationStrategy.onAuthentication(auth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Web girişinde bunu Spring Security'nin oturum stratejisi yapar; burada oturum el ile açıldığı için kayıt da
        // el ile yapılır. Kayıtsız mobil oturumu yönetim izleci göstermez ve yönetici sonlandıramazdı.
        sessionRegistry.registerNewSession(request.getSession().getId(), details);
    }
}
