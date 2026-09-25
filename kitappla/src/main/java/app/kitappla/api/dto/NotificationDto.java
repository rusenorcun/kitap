package app.kitappla.api.dto;

public record NotificationDto(
        Long id,
        String type,
        String message,
        boolean read,
        String link,
        String createdAt
) {}
