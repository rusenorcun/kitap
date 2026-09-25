package app.kitappla.api.dto;

public record ChatMessageDto(
        Long id,
        String body,
        boolean mine,
        String senderName,
        String createdAt
) {}
