package app.kitappla.api.dto;

public record PickupPointDto(
        Long id,
        String campus,
        String name,
        String description,
        boolean active
) {}
