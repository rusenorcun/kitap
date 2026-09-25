package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportBody(
        @NotBlank String reason,
        String note
) {}
