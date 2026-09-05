package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterBody(
        @NotBlank String name,
        @NotBlank String email,
        @NotBlank String password,
        String school,
        String level,
        String phone,
        String address
) {}
