package app.kitapla.api.dto;

public record EligibilityDto(
        boolean allowed,
        String code,
        String reason
) {}
