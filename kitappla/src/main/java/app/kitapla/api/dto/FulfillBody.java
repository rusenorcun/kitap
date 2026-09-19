package app.kitapla.api.dto;

public record FulfillBody(
        String source
) {
    public FulfillBody {
        if (source == null || source.isBlank()) source = "OWN";
    }
}
