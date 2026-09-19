package app.kitapla.api.dto;

public record DonationDto(
        Long id,
        BookDto book,
        String donorName,
        String donorInitials,
        String description,
        int quantity,
        long claimed,
        long remaining,
        String source,
        String targetLevel,
        String status,
        boolean priorityActive,
        String priorityLeft,
        PickupPointDto point,
        String createdAt,
        EligibilityDto eligibility
) {}
