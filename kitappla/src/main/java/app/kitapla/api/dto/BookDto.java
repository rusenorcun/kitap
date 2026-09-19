package app.kitapla.api.dto;

public record BookDto(
        Long id,
        String title,
        String author,
        String coverUrl,
        String purchaseLink,
        String description
) {}
