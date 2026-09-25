package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ArrangeMeetingBody(
        Long pointId,
        String note,
        @NotBlank String at
) {}
