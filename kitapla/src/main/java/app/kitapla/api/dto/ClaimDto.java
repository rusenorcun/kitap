package app.kitapla.api.dto;

public record ClaimDto(
        Long id,
        String status,
        String requesterName,
        String requesterInitials,
        String address,
        String phone,
        MeetingDto meeting,
        Long conversationId,
        String createdAt
) {}
