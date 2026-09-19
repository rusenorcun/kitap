package app.kitapla.api.dto;

public record FeaturesDto(
        boolean shipping,
        boolean purchase,
        boolean address,
        boolean messaging,
        boolean reports
) {}
