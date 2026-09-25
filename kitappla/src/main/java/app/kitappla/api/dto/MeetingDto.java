package app.kitappla.api.dto;

public record MeetingDto(
        PickupPointDto point,
        String note,
        String at,
        String arrangedAt,
        String remindedAt
) {}
