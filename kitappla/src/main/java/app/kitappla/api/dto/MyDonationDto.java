package app.kitappla.api.dto;

import java.util.List;

public record MyDonationDto(
        Long id,
        BookDto book,
        int quantity,
        long claimed,
        long remaining,
        String source,
        String targetLevel,
        String status,
        List<ClaimDto> claims,
        PickupPointDto point,
        String pointNote,
        String createdAt
) {}
