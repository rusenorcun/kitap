package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSwapBookBody(
        @NotBlank String title,
        String author,
        String note,
        String purchaseLink,
        String coverUrl
) {}
