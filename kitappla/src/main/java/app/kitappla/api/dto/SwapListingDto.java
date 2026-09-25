package app.kitappla.api.dto;

public record SwapListingDto(
        Long id,
        BookDto book,
        String note,
        String status,
        String ownerName,
        String ownerInitials,
        String createdAt
) {}
