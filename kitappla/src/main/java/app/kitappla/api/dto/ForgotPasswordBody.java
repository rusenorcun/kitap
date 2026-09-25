package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordBody(
        @NotBlank String email
) {}
