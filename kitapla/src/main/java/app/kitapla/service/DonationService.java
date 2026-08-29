package app.kitapla.service;

import app.kitapla.config.Features;
import app.kitapla.domain.*;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.repo.DonationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Bağış okuma tarafı ve "kitabı alabilir mi?" kuralları.
 * Kurallar tek yerde tutulur; şablonlar buton durumunu, Faz 2'deki claim işlemi
 * ise fiilî engellemeyi buradan alır.
 */
@Service
public class DonationService {

    private final Features features;
    private final MeetingService meetings;
    private final PickupPointService points;
    private final UserRepository users;
    private final DonationRepository donations;
    private final ClaimRepository claims;
    private final QuotaService quotaService;
    private final NotificationService notifications;

    public DonationService(Features features, MeetingService meetings, PickupPointService points,
                           UserRepository users,
                           DonationRepository donations, ClaimRepository claims,
                           QuotaService quotaService, NotificationService notifications) {
        this.features = features;
        this.meetings = meetings;
        this.points = points;
        this.users = users;
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
        long claimed = claims.countByDonationAndStatusNot(d, ClaimStatus.NO_SHOW);
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

        // Adres yalnızca kargo akışında gerekir; kampüs içi yüz yüze teslimde istenmez
        if (features.isAddress() && (user.getAddress() == null || user.getAddress().isBlank()))
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
        long taken = claims.countByDonationAndStatusNot(d, ClaimStatus.NO_SHOW);
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

    // ---------- Bağış oluşturma / yönetimi ----------

    /** Yeni bağış yayınlar. Kitap zaten BookService ile bulunmuş/oluşturulmuş olmalıdır. */
    @Transactional
    public Donation create(User donor, Book book, int quantity, TargetLevel level,
                           DonationSource source, String description) {
        return create(donor, book, quantity, level, source, description, null, null);
    }

    /** Kampüs teslimi için bağışçının önerdiği noktayla birlikte. */
    @Transactional
    public Donation create(User donor, Book book, int quantity, TargetLevel level,
                           DonationSource source, String description,
                           Long preferredPointId, String preferredPointNote) {
        if (book == null) throw new IllegalStateException("Kitap seçilmedi.");
        if (quantity < 1) throw new IllegalStateException("Adet en az 1 olmalı.");
        if (quantity > 50) throw new IllegalStateException("Tek seferde en fazla 50 adet bağışlayabilirsin.");
        if (features.isAddress() && (donor.getAddress() == null || donor.getAddress().isBlank()))
            throw new IllegalStateException("Bağış yapmadan önce profilinden iletişim/teslimat adresi eklemelisin.");

        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(book);
        d.setQuantity(quantity);
        d.setTargetLevel(level == null ? TargetLevel.HEPSI : level);
        d.setSource(source == null ? DonationSource.PURCHASE : source);
        d.setDescription(description);

        // Kampüs içi teslimde bağışçı bir nokta önerir; taraflar sonra değiştirebilir
        if (preferredPointId != null) {
            d.setPreferredPoint(points.findSelectable(preferredPointId).orElseThrow(
                    () -> new IllegalStateException("Seçtiğin teslim noktası kullanılmıyor.")));
        }
        String not = preferredPointNote == null ? null : preferredPointNote.trim();
        d.setPreferredPointNote(not == null || not.isEmpty() ? null
                : (not.length() > 300 ? not.substring(0, 300) : not));

        return donations.save(d);
    }

    @Transactional(readOnly = true)
    public List<DonationView> myDonations(User donor) {
        return donations.findByDonorWithDetails(donor).stream().map(this::toView).toList();
    }

    /** Bağışçının kendi bağışını kapatması (yeni talep alınmaz). */
    @Transactional
    public void close(Long donationId, User donor) {
        Donation d = ownDonation(donationId, donor);
        d.setStatus(DonationStatus.CLOSED);
        donations.save(d);
    }

    /** Bağışçının kendi bağışını yeniden açması. */
    @Transactional
    public void reopen(Long donationId, User donor) {
        Donation d = ownDonation(donationId, donor);
        long taken = claims.countByDonationAndStatusNot(d, ClaimStatus.NO_SHOW);
        if (taken >= d.getQuantity())
            throw new IllegalStateException("Bu bağışta kalan kitap yok; yeniden açılamaz.");
        d.setStatus(DonationStatus.OPEN);
        donations.save(d);
    }

    /** Talep alınmamış bağışı siler. */
    @Transactional
    public void delete(Long donationId, User donor) {
        Donation d = ownDonation(donationId, donor);
        if (claims.countByDonation(d) > 0)
            throw new IllegalStateException("Talep alınmış bir bağış silinemez; bunun yerine kapatabilirsin.");
        donations.delete(d);
    }

    private Donation ownDonation(Long donationId, User donor) {
        Donation d = donations.findByIdWithDetails(donationId)
                .orElseThrow(() -> new IllegalStateException("Bağış bulunamadı."));
        if (!d.getDonor().getId().equals(donor.getId()))
            throw new IllegalStateException("Bu bağış sana ait değil.");
        return d;
    }

    // ---------- Teslimat akışı ----------

    /** Bağışçı kargoya verdi. */
    @Transactional
    public void ship(Long claimId, User donor) {
        Claim c = claimOfDonor(claimId, donor);
        if (c.getStatus() != ClaimStatus.MATCHED)
            throw new IllegalStateException("Bu kayıt zaten kargolanmış.");
        c.setStatus(ClaimStatus.SHIPPED);
        c.setShippedAt(Instant.now());
        claims.save(c);
        notifications.notify(c.getStudent(), "claim_shipped",
                "\"" + c.getDonation().getBook().getTitle() + "\" kitabın kargoya verildi.");
    }

    /**
     * Buluşmayı ayarlar ya da günceller. Kampüs içi teslimde taraflardan
     * <b>ikisi de</b> yapabilir: bağışçı bir yer önerir, alıcı mesajlaşma
     * sonrasında değiştirebilir.
     */
    @Transactional
    public Claim arrange(Long claimId, User user, MeetingRequest request) {
        Claim c = claims.findByIdWithDetails(claimId)
                .orElseThrow(() -> new IllegalStateException("Kayıt bulunamadı."));

        boolean bagisci = c.getDonation().getDonor().getId().equals(user.getId());
        boolean alici = c.getStudent().getId().equals(user.getId());
        if (!bagisci && !alici)
            throw new IllegalStateException("Bu kayıt sana ait değil.");
        if (c.getStatus() == ClaimStatus.DELIVERED)
            throw new IllegalStateException("Bu kitap zaten teslim edildi.");

        meetings.apply(c.getMeeting(), request);
        c.setStatus(ClaimStatus.ARRANGED);
        claims.save(c);

        User digeri = bagisci ? c.getStudent() : c.getDonation().getDonor();
        notifications.notify(digeri, "meeting_arranged",
                "\"" + c.getDonation().getBook().getTitle() + "\" için buluşma ayarlandı: "
                        + meetings.summary(c.getMeeting()));
        return c;
    }

    /**
     * Karşı taraf buluşmaya gelmedi. Yalnızca buluşma saati geçtikten sonra ve
     * yalnızca alışverişin diğer tarafınca bildirilebilir.
     * <p>
     * Kitap havuza geri döner (kalan adet hesabına dahil edilmez) ama alıcının
     * kota hakkı yanar — aksi hâlde gelmemek, kotayı sıfırlamanın yolu olurdu.
     */
    @Transactional
    public Claim noShow(Long claimId, User bildiren) {
        Claim c = claims.findByIdWithDetails(claimId)
                .orElseThrow(() -> new IllegalStateException("Kayıt bulunamadı."));

        boolean bagisci = c.getDonation().getDonor().getId().equals(bildiren.getId());
        boolean alici = c.getStudent().getId().equals(bildiren.getId());
        if (!bagisci && !alici) throw new IllegalStateException("Bu kayıt sana ait değil.");

        if (c.getStatus() == ClaimStatus.DELIVERED)
            throw new IllegalStateException("Bu kitap zaten teslim edilmiş.");
        if (c.getStatus() == ClaimStatus.NO_SHOW)
            throw new IllegalStateException("Bu kayıt zaten gelinmedi olarak işaretlenmiş.");
        if (!c.getMeeting().isArranged())
            throw new IllegalStateException("Önce bir buluşma ayarlanmış olmalı.");
        if (c.getMeeting().getAt() != null && Instant.now().isBefore(c.getMeeting().getAt()))
            throw new IllegalStateException("Buluşma saati daha gelmedi.");

        c.setStatus(ClaimStatus.NO_SHOW);
        claims.save(c);

        User gelmeyen = bagisci ? c.getStudent() : c.getDonation().getDonor();
        gelmeyen.setNoShowCount(gelmeyen.getNoShowCount() + 1);
        users.save(gelmeyen);

        String kitap = c.getDonation().getBook().getTitle();
        notifications.notify(gelmeyen, "gelmedi",
                "\"" + kitap + "\" buluşmasına gelmediğin bildirildi. "
                        + "Tekrarlanırsa hesabın askıya alınabilir.");
        notifications.notify(bildiren, "gelmedi_kayit",
                "\"" + kitap + "\" için gelinmedi bildirimin kaydedildi. Kitap yeniden başkalarına açıldı.");
        return c;
    }

    /** Alıcı teslim aldı. */
    @Transactional
    public void deliver(Long claimId, User receiver) {
        Claim c = claimOfReceiver(claimId, receiver);
        if (c.getStatus() == ClaimStatus.DELIVERED)
            throw new IllegalStateException("Bu kitabı zaten teslim aldın.");
        // Yüz yüze teslimde önce buluşma ayarlanmış olmalı; kargo modunda gerekmez
        if (features.isHandover() && !features.isShipping() && c.getStatus() == ClaimStatus.MATCHED)
            throw new IllegalStateException("Önce buluşma ayarlayın, sonra teslimi onaylayın.");
        c.setStatus(ClaimStatus.DELIVERED);
        c.setDeliveredAt(Instant.now());
        claims.save(c);
        notifications.notify(c.getDonation().getDonor(), "claim_delivered",
                receiver.getName() + ", \"" + c.getDonation().getBook().getTitle() + "\" kitabını teslim aldı.");
    }

    /** Alıcı teslim sonrası bağışçıya teşekkür eder. */
    @Transactional
    public void thank(Long claimId, User receiver, String message) {
        Claim c = claimOfReceiver(claimId, receiver);
        if (c.getStatus() != ClaimStatus.DELIVERED)
            throw new IllegalStateException("Teşekkür notu yalnızca teslim aldığın kitaplar için gönderilebilir.");
        String note = (message == null || message.isBlank()) ? "" : " Notu: \"" + message.trim() + "\"";
        notifications.notify(c.getDonation().getDonor(), "thank_you",
                receiver.getName() + ", \"" + c.getDonation().getBook().getTitle() + "\" bağışın için teşekkür etti." + note);
    }

    /** Alıcı, kargolanmadan önce talebini iptal eder; adet geri açılır. */
    @Transactional
    public void cancelClaim(Long claimId, User receiver) {
        Claim c = claimOfReceiver(claimId, receiver);
        if (c.getStatus() != ClaimStatus.MATCHED)
            throw new IllegalStateException("Kargolanmış ya da teslim edilmiş bir talep iptal edilemez.");
        Donation d = c.getDonation();
        claims.delete(c);
        if (d.getStatus() == DonationStatus.CLOSED) {
            d.setStatus(DonationStatus.OPEN);
            donations.save(d);
        }
        notifications.notify(d.getDonor(), "claim_cancelled",
                receiver.getName() + ", \"" + d.getBook().getTitle() + "\" bağışından aldığı kitabı iptal etti.");
    }

    private Claim claimOfDonor(Long claimId, User donor) {
        Claim c = claims.findByIdWithDetails(claimId)
                .orElseThrow(() -> new IllegalStateException("Teslimat kaydı bulunamadı."));
        if (!c.getDonation().getDonor().getId().equals(donor.getId()))
            throw new IllegalStateException("Bu teslimat kaydı sana ait değil.");
        return c;
    }

    private Claim claimOfReceiver(Long claimId, User receiver) {
        Claim c = claims.findByIdWithDetails(claimId)
                .orElseThrow(() -> new IllegalStateException("Teslimat kaydı bulunamadı."));
        if (!c.getStudent().getId().equals(receiver.getId()))
            throw new IllegalStateException("Bu teslimat kaydı sana ait değil.");
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