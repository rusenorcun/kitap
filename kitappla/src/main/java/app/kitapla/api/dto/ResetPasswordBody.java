package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordBody(
        @NotBlank String token,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {}
