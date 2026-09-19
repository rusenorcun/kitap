package app.kitapla.api.dto;

public record QuotaDto(
        String tier,
        long weeklyUsed,
        int weeklyLimit,
        long weeklyRemaining,
        long monthlyUsed,
        int monthlyLimit,
        long monthlyRemaining,
        boolean canReceive
) {}
