package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SwapStatusBody(
        @NotBlank String status
) {}
