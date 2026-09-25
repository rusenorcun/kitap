package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentEmailBody(
        @NotBlank String email,
        String level,
        String school
) {}
