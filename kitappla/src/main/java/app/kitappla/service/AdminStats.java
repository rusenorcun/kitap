package app.kitappla.service;

/** Yönetim panosundaki sayaçlar (tek sorgu turunda toplanır). */
public record AdminStats(
        long users,
        long students,
        long pendingDocuments,
        long blocked,
        long admins,
        long books,
        long openDonations,
        long closedDonations,
        long matchedClaims,
        long shippedClaims,
        long deliveredClaims,
        long openRequests,
        long fulfilledRequests,
        long openSwapBooks,
        long pendingOffers,
        long completedOffers
) {
}
