package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageBody(
        @NotBlank String body
) {}
