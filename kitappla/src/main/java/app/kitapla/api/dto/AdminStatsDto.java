package app.kitapla.api.dto;

public record AdminStatsDto(
        int totalUsers,
        int pendingDocs,
        int donations,
        int delivered
) {}
