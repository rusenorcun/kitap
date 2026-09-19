package app.kitapla.api.dto;

public record MyClaimDto(
        Long id,
        String status,
        BookDto book,
        String donorName,
        String donorInitials,
        MeetingDto meeting,
        Long conversationId,
        String createdAt,
        String arrangedAt,
        String shippedAt,
        String deliveredAt
) {}
