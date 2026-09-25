package app.kitappla.api.dto;

public record AdminStatsDto(
        int totalUsers,
        int pendingDocs,
        int donations,
        int delivered
) {}
