package app.kitapla.service;

public record Quota(
        String tier,
        long weeklyUsed, int weeklyLimit, long weeklyRemaining,
        long monthlyUsed, int monthlyLimit, long monthlyRemaining,
        boolean canReceive
) {}
