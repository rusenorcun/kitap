package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginBody(
        @NotBlank String email,
        @NotBlank String password
) {}
