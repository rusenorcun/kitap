package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateBody(
        @NotBlank String name,
        String address,
        String phone,
        String school
) {}
