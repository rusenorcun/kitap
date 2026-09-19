package app.kitapla.api.dto;

public record BookMetadataDto(
        boolean found,
        String title,
        String author,
        String coverUrl,
        String purchaseLink,
        String description
) {}
