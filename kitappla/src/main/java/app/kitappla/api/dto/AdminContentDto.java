package app.kitappla.api.dto;

import java.util.List;

public record AdminContentDto(
        List<DonationDto> donations,
        List<RequestDto> requests,
        List<SwapListingDto> swaps
) {}
