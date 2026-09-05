package app.kitapla.api.dto;

public record ConversationDto(
        Long id,
        String kind,
        Long refId,
        String title,
        String counterpartName,
        String counterpartInitials,
        String lastMessage,
        String lastAt,
        long unread
) {}
