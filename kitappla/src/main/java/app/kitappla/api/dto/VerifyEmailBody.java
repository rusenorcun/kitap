package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailBody(
        @NotBlank String token
) {}
