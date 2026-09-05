package app.kitapla.api.dto;

public record NotificationDto(
        Long id,
        String type,
        String message,
        boolean read,
        String createdAt
) {}
