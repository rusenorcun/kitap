package app.kitapla.service;

import app.kitapla.domain.RequestStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.ClaimRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class QuotaService {

    private final ClaimRepository claims;
    private final BookRequestRepository requests;

    public QuotaService(ClaimRepository claims, BookRequestRepository requests) {
        this.claims = claims;
        this.requests = requests;
    }

    private static final List<RequestStatus> FULFILLED =
            List.of(RequestStatus.FULFILLED, RequestStatus.SHIPPED, RequestStatus.DELIVERED);

    private long countWindow(User user, Duration window) {
        Instant after = Instant.now().minus(window);
        return claims.countByStudentAndCreatedAtAfter(user, after)
                + requests.countByStudentAndStatusInAndFulfilledAtAfter(user, FULFILLED, after);
    }

    public Quota quotaFor(User user) {
        boolean student = user.isStudent();
        int weeklyLimit = student ? 3 : 1;
        int monthlyLimit = student ? 10 : 3;
        long weeklyUsed = countWindow(user, Duration.ofDays(7));
        long monthlyUsed = countWindow(user, Duration.ofDays(30));
        boolean can = weeklyUsed < weeklyLimit && monthlyUsed < monthlyLimit;
        return new Quota(
                student ? "student" : "member",
                weeklyUsed, weeklyLimit, Math.max(0, weeklyLimit - weeklyUsed),
                monthlyUsed, monthlyLimit, Math.max(0, monthlyLimit - monthlyUsed),
                can
        );
    }

    /** Alabiliyorsa null; alamıyorsa sebep mesajı döner. */
    public String cannotReceiveReason(User user) {
        Quota q = quotaFor(user);
        if (q.weeklyUsed() >= q.weeklyLimit())
            return "Haftalık " + q.weeklyLimit() + " kitap sınırına ulaştınız.";
        if (q.monthlyUsed() >= q.monthlyLimit())
            return "30 günlük " + q.monthlyLimit() + " kitap sınırına ulaştınız.";
        return null;
    }
}
