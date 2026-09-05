package app.kitapla.api.v1;

import app.kitapla.api.dto.*;
import app.kitapla.domain.School;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.security.LoginAttemptService;
import app.kitapla.service.PasswordResetService;
import app.kitapla.service.Quota;
import app.kitapla.service.QuotaService;
import app.kitapla.service.UserService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthApiController(UserRepository users,
                             UserService userService,
                             QuotaService quotaService,
                             PasswordResetService passwordResetService,
                             PasswordEncoder passwordEncoder,
                             LoginAttemptService loginAttempts) {
        this.users = users;
        this.userService = userService;
        this.quotaService = quotaService;
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
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
        if (user == null || !passwordEncoder.matches(body.password(), user.getPasswordHash())) {
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
        School school = null;
        if (body.school() != null && !body.school().isBlank()) {
            try {
                school = School.valueOf(body.school().trim());
            } catch (IllegalArgumentException e) {
                // Ignore or handle invalid enum
            }
        }

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
                null
        );

        setAuthenticatedUser(user, request, response);

        Quota quota = quotaService.quotaFor(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiDtoMapper.toMeDto(user, quota));
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
        passwordResetService.request(body.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageDto("Şifre sıfırlama bağlantısı e-posta adresine gönderildi."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordBody body) {
        passwordResetService.reset(body.token(), body.newPassword(), body.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    private void setAuthenticatedUser(User user, HttpServletRequest request, HttpServletResponse response) {
        AppUserDetails details = new AppUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
