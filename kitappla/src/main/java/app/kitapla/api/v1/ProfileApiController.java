package app.kitapla.api.v1;

import app.kitapla.api.dto.*;
import app.kitapla.domain.School;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.Quota;
import app.kitapla.service.QuotaService;
import app.kitapla.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProfileApiController {

    private final UserService userService;
    private final QuotaService quotaService;

    public ProfileApiController(UserService userService, QuotaService quotaService) {
        this.userService = userService;
        this.quotaService = quotaService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeDto> getProfile() {
        User user = CurrentUser.get();
        if (user == null) {
            throw new IllegalStateException("Giriş yapmalısınız.");
        }
        Quota quota = quotaService.quotaFor(user);
        return ResponseEntity.ok(ApiDtoMapper.toMeDto(user, quota));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateProfile(@Valid @RequestBody ProfileUpdateBody body) {
        User user = CurrentUser.get();
        if (user == null) {
            throw new IllegalStateException("Giriş yapmalısınız.");
        }

        School school = School.of(body.school());

        User updated = userService.updateProfile(user, body.name(), body.address(), body.phone(), school);
        return ResponseEntity.ok(ApiDtoMapper.toUserDto(updated));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeBody body) {
        User user = CurrentUser.get();
        if (user == null) {
            throw new IllegalStateException("Giriş yapmalısınız.");
        }
        userService.changePassword(user, body.currentPassword(), body.newPassword(), body.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/student")
    public ResponseEntity<UserDto> verifyStudent(@Valid @RequestBody StudentEmailBody body) {
        User user = CurrentUser.get();
        if (user == null) {
            throw new IllegalStateException("Giriş yapmalısınız.");
        }
        User updated = userService.verifyStudentEmail(user, body.email());
        return ResponseEntity.ok()
                .header("X-Student-Verification-Sent", Boolean.toString(updated.isStudentVerificationSent()))
                .body(ApiDtoMapper.toUserDto(updated));
    }

    public record StudentConfirmationBody(@jakarta.validation.constraints.NotBlank String token) {}

    @PostMapping("/me/student/confirm")
    public ResponseEntity<UserDto> confirmStudent(@Valid @RequestBody StudentConfirmationBody body) {
        User user = CurrentUser.get();
        if (user == null) throw new IllegalStateException("Giriş yapmalısınız.");
        return ResponseEntity.ok(ApiDtoMapper.toUserDto(userService.confirmStudentEmail(user, body.token())));
    }

    @GetMapping("/quota")
    public ResponseEntity<QuotaDto> getQuota() {
        User user = CurrentUser.get();
        if (user == null) {
            throw new IllegalStateException("Giriş yapmalısınız.");
        }
        Quota quota = quotaService.quotaFor(user);
        return ResponseEntity.ok(ApiDtoMapper.toQuotaDto(quota));
    }
}
