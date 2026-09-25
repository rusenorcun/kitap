package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeBody(
        @NotBlank String currentPassword,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {}
