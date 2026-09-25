package app.kitappla.api.dto;

public record EligibilityDto(
        boolean allowed,
        String code,
        String reason
) {}
