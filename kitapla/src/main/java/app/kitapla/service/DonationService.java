package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.DonationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Bağış okuma tarafı ve "kitabı alabilir mi?" kuralları.
 * Kurallar tek yerde tutulur; şablonlar buton durumunu, Faz 2'deki claim işlemi
 * ise fiilî engellemeyi buradan alır.
 */
@Service
public class DonationService {

    private final DonationRepository donations;
    private final ClaimRepository claims;
    private final QuotaService quotaService;
    private final NotificationService notifications;

    public DonationService(DonationRepository donations, ClaimRepository claims,
                           QuotaService quotaService, NotificationService notifications) {
        this.donations = donations;
        this.claims = claims;
        this.quotaService = quotaService;
        this.notifications = notifications;
    }

    /** Bağış filtresi. Boş alanlar "filtreleme yok" demektir. */
    public record Filter(TargetLevel level, String query, Long bookId, boolean onlyAvailable) {
        public static Filter none() { return new Filter(null, null, null, true); }
    }

    private DonationView toView(Donation d) {
        long claimed = claims.countByDonation(d);
        return new DonationView(d, claimed, Math.max(0, d.getQuantity() - claimed));
    }

    @Transactional(readOnly = true)
    public List<DonationView> openDonations(Filter filter) {
        Filter f = filter == null ? Filter.none() : filter;
        String q = f.query() == null ? null : f.query().trim().toLowerCase();

        return donations.findOpenWithDetails(DonationStatus.OPEN).stream()
                .filter(d -> {
                    // Seviye: tam eşleşme ya da "hepsi"ne açık bağışlar
                    if (f.level() != null && d.getTargetLevel() != TargetLevel.HEPSI
                            && d.getTargetLevel() != f.level()) return false;
                    if (f.bookId() != null && !f.bookId().equals(d.getBook().getId())) return false;
                    if (q != null && !q.isBlank()) {
                        String title = d.getBook().getTitle() == null ? "" : d.getBook().getTitle().toLowerCase();
                        String author = d.getBook().getAuthor() == null ? "" : d.getBook().getAuthor().toLowerCase();
                        if (!title.contains(q) && !author.contains(q)) return false;
                    }
                    return true;
                })
                .map(this::toView)
                .filter(v -> !f.onlyAvailable() || v.isAvailable())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DonationView> view(Long id) {
        return donations.findByIdWithDetails(id).map(this::toView);
    }

    /**
     * Kullanıcı bu bağıştan kitap alabilir mi? Sıra önemlidir: en açıklayıcı sebep döner.
     */
    @Transactional(readOnly = true)
    public ClaimEligibility eligibility(DonationView view, User user) {
        if (view == null) return ClaimEligibility.deny("NOT_FOUND", "Bağış bulunamadı.");
        if (user == null) return ClaimEligibility.deny("LOGIN_REQUIRED", "Kitap almak için giriş yapmalısın.");
        if (user.isBlocked()) return ClaimEligibility.deny("BLOCKED", "Hesabın engellenmiş.");

        Donation d = view.donation();
        if (d.getStatus() != DonationStatus.OPEN || !view.isAvailable())
            return ClaimEligibility.deny("SOLD_OUT", "Bu bağışta kalan kitap yok.");

        if (d.getDonor().getId().equals(user.getId()))
            return ClaimEligibility.deny("OWN_DONATION", "Kendi bağışından kitap alamazsın.");

        if (user.getAddress() == null || user.getAddress().isBlank())
            return ClaimEligibility.deny("ADDRESS_REQUIRED", "Önce profilinden teslimat adresi eklemelisin.");

        if (d.getTargetLevel() != TargetLevel.HEPSI && d.getTargetLevel() != asTarget(user.getSchoolLevel()))
            return ClaimEligibility.deny("LEVEL_MISMATCH", "Bu bağış senin okul seviyene uygun değil.");

        // Öğrenci önceliği: pencere açıkken yalnızca onaylı öğrenciler alabilir
        if (view.isPriorityActive() && !user.isStudent())
            return ClaimEligibility.deny("PRIORITY_WINDOW",
                    "Bu bağış şu an öğrencilere öncelikli. Üyelere " + view.getPriorityLeft() + " sonra açılacak.");

        if (claims.existsByDonationAndStudent(d, user))
            return ClaimEligibility.deny("ALREADY_CLAIMED", "Bu bağıştan zaten bir kitap aldın.");

        String quotaReason = quotaService.cannotReceiveReason(user);
        if (quotaReason != null) return ClaimEligibility.deny("QUOTA_FULL", quotaReason);

        return ClaimEligibility.ok();
    }

    /**
     * Kullanıcı bağıştan bir kitap alır. Kurallar {@link #eligibility} ile aynı kaynaktan gelir;
     * adet kontrolü yarış durumlarına karşı işlem içinde yeniden yapılır.
     *
     * @return oluşturulan talep
     * @throws IllegalStateException kural ihlalinde (mesaj kullanıcıya gösterilebilir)
     */
    @Transactional
    public Claim claim(Long donationId, User user) {
        Donation d = donations.findByIdWithDetails(donationId)
                .orElseThrow(() -> new IllegalStateException("Bağış bulunamadı."));

        DonationView v = toView(d);
        ClaimEligibility e = eligibility(v, user);
        if (!e.allowed()) throw new IllegalStateException(e.reason());

        // Yarış durumu: adet kontrolünü işlem içinde tekrarla
        long taken = claims.countByDonation(d);
        if (taken >= d.getQuantity()) throw new IllegalStateException("Bu bağışta kalan kitap yok.");

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(user);
        c = claims.save(c);

        if (taken + 1 >= d.getQuantity()) {
            d.setStatus(DonationStatus.CLOSED);
            donations.save(d);
        }

        notifications.notify(d.getDonor(), "donation_claimed",
                user.getName() + ", \"" + d.getBook().getTitle() + "\" bağışından bir kitap aldı.");
        return c;
    }

    private static TargetLevel asTarget(SchoolLevel level) {
        if (level == null) return null;
        return switch (level) {
            case ORTAOKUL -> TargetLevel.ORTAOKUL;
            case LISE -> TargetLevel.LISE;
            case UNIVERSITE -> TargetLevel.UNIVERSITE;
        };
    }
}