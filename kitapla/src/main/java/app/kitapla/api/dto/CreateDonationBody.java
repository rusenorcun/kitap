package app.kitapla.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateDonationBody(
        @NotBlank String title,
        String author,
        String purchaseLink,
        @Min(1) int quantity,
        String targetLevel,
        String source,
        String description,
        String coverUrl,
        Long pointId,
        String pointNote
) {
    public CreateDonationBody {
        if (quantity < 1) quantity = 1;
        if (targetLevel == null || targetLevel.isBlank()) targetLevel = "HEPSI";
        if (source == null || source.isBlank()) source = "OWN";
    }
}
