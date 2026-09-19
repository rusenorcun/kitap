package app.kitapla.api.dto;

import jakarta.validation.constraints.NotNull;

public record CreateOfferBody(
        @NotNull Long targetBookId,
        @NotNull Long offeredBookId,
        String message
) {}
