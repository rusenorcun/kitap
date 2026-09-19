package app.kitapla.api.dto;

public record OfferDto(
        Long id,
        String direction,
        String status,
        String message,
        String counterpartName,
        String counterpartInitials,
        BookDto takeBook,
        BookDto giveBook,
        boolean mineHandedOver,
        boolean theirsHandedOver,
        boolean addressVisible,
        MeetingDto meeting,
        Long conversationId,
        String createdAt
) {}
