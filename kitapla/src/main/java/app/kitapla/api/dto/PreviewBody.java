package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PreviewBody(
        @NotBlank String purchaseLink
) {}
