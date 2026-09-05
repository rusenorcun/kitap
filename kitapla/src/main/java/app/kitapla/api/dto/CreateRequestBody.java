package app.kitapla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRequestBody(
        @NotBlank String title,
        String author,
        String purchaseLink,
        String description
) {}
