package app.kitapla.api.dto;

public record RequestDto(
        Long id,
        BookDto book,
        String requesterName,
        String requesterInitials,
        String description,
        String source,
        String status,
        String fulfilledByName,
        String fulfilledByInitials,
        MeetingDto meeting,
        Long conversationId,
        String createdAt
) {}
