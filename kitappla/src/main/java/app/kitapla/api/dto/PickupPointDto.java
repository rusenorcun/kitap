package app.kitapla.api.dto;

public record PickupPointDto(
        Long id,
        String name,
        String description,
        boolean active
) {}
