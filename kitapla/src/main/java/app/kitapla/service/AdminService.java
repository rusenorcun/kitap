package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Yönetim işlemleri: öğrenci belgesi onayı, üye yönetimi ve içerik moderasyonu.
 * Her işlem ilgili üyeye bildirim bırakır; sessiz müdahale yapılmaz.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final BookRepository books;
    private final DonationRepository donations;
    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapBookRepository swapBooks;
    private final SwapOfferRepository swapOffers;
    private final NotificationRepository notificationRepo;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final NotificationService notifications;
    private final Path documentDir;

    public AdminService(UserRepository users, BookRepository books, DonationRepository donations,
                        ClaimRepository claims, BookRequestRepository requests, SwapBookRepository swapBooks,
                        SwapOfferRepository swapOffers, NotificationRepository notificationRepo,
                        ConversationRepository conversations, MessageRepository messages,
                        NotificationService notifications,
                        @Value("${kitapla.upload-dir}") String uploadDir) {
        this.users = users;
        this.books = books;
        this.donations = donations;
        this.claims = claims;
        this.requests = requests;
        this.swapBooks = swapBooks;
        this.swapOffers = swapOffers;
        this.notificationRepo = notificationRepo;
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
        this.documentDir = Path.of(uploadDir, "documents");
    }

    // ---------- Pano ----------

    public AdminStats stats() {
        return new AdminStats(
                users.count(),
                users.countByStudentStatus(StudentStatus.APPROVED),
                users.countByStudentStatus(StudentStatus.PENDING),
                users.countByBlockedTrue(),
                users.countByAdminTrue(),
                books.count(),
                donations.countByStatus(DonationStatus.OPEN),
                donations.countByStatus(DonationStatus.CLOSED),
                claims.countByStatus(ClaimStatus.MATCHED),
                claims.countByStatus(ClaimStatus.SHIPPED),
                claims.countByStatus(ClaimStatus.DELIVERED),
                requests.countByStatus(RequestStatus.OPEN),
                requests.countByStatus(RequestStatus.FULFILLED),
                swapBooks.countByStatus(SwapBookStatus.OPEN),
                swapOffers.countByStatus(OfferStatus.PENDING),
                swapOffers.countByStatus(OfferStatus.COMPLETED)
        );
    }

    // ---------- Öğrenci belgeleri ----------

    /** Yönetim navigasyonundaki rozet için. */
    public long pendingDocumentCount() {
        return users.countByStudentStatus(StudentStatus.PENDING);
    }

    public List<User> pendingDocuments() {
        return users.findByStudentStatusOrderByCreatedAtDesc(StudentStatus.PENDING);
    }

    /** İncelemedeki bir belgeyi onaylar; üye öğrenci ayrıcalıklarını kazanır. */
    @Transactional
    public User approveStudent(Long userId) {
        User u = pending(userId);
        u.setStudentStatus(StudentStatus.APPROVED);
        users.save(u);
        notifications.notify(u, "belge",
                "Öğrenci belgen onaylandı. Artık bağışlarda 48 saat öncelik ve daha yüksek kota kullanıyorsun.");
        return u;
    }

    /**
     * Belgeyi reddeder. Reddedilen belge diskte tutulmaz; üye yeni belgeyle
     * tekrar başvurabilir (belge numarası kendisinde kalır).
     */
    @Transactional
    public User rejectStudent(Long userId, String reason) {
        User u = pending(userId);
        String note = reason == null || reason.isBlank() ? null : reason.trim();

        deleteDocument(u.getDocumentPath());
        u.setDocumentPath(null);
        u.setStudentStatus(StudentStatus.REJECTED);
        users.save(u);

        notifications.notify(u, "belge", note == null
                ? "Öğrenci belgen doğrulanamadı. Profilinden yeni bir belgeyle tekrar başvurabilirsin."
                : "Öğrenci belgen doğrulanamadı: " + note + " Profilinden tekrar başvurabilirsin.");
        return u;
    }

    private User pending(Long userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Üye bulunamadı."));
        if (u.getStudentStatus() != StudentStatus.PENDING)
            throw new IllegalStateException("Bu başvuru zaten sonuçlanmış.");
        return u;
    }

    /** Belgeyi yalnızca yönetim okuyabilsin diye dosya yolu servis üzerinden çözülür. */
    public Path documentPathOf(Long userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Üye bulunamadı."));
        if (u.getDocumentPath() == null || u.getDocumentPath().isBlank())
            throw new IllegalStateException("Bu üyenin yüklü belgesi yok.");
        // Dosya adı dışına çıkan yollar (../) kabul edilmez
        Path file = documentDir.resolve(u.getDocumentPath()).normalize();
        if (!file.startsWith(documentDir.normalize()))
            throw new IllegalStateException("Belge yolu geçersiz.");
        if (!Files.isRegularFile(file))
            throw new IllegalStateException("Belge dosyası bulunamadı.");
        return file;
    }

    private void deleteDocument(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            Path file = documentDir.resolve(fileName).normalize();
            if (file.startsWith(documentDir.normalize())) Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // Dosya temizliği başarısız olsa da kaydın durumu güncellenmeli
        }
    }

    // ---------- Üye yönetimi ----------

    public List<User> searchUsers(String q) {
        if (q == null || q.isBlank()) return users.findTop200ByOrderByCreatedAtDesc();
        String term = q.trim();
        return users.findTop200ByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(term, term);
    }

    /** Askıya alınan üye giriş yapamaz; mevcut kayıtları durur. */
    @Transactional
    public User setBlocked(User admin, Long userId, boolean blocked) {
        User u = target(admin, userId, blocked ? "Kendini askıya alamazsın." : "Kendini işleme alamazsın.");
        if (blocked && u.isAdmin())
            throw new IllegalStateException("Yöneticiler askıya alınamaz; önce yetkisini kaldır.");
        if (u.isBlocked() == blocked)
            throw new IllegalStateException(blocked ? "Üye zaten askıda." : "Üye zaten aktif.");

        u.setBlocked(blocked);
        users.save(u);
        notifications.notify(u, "hesap", blocked
                ? "Hesabın yönetim tarafından askıya alındı. İtiraz için iletişime geçebilirsin."
                : "Hesabının askısı kaldırıldı; tekrar giriş yapabilirsin.");
        return u;
    }

    @Transactional
    public User setAdmin(User admin, Long userId, boolean makeAdmin) {
        User u = target(admin, userId, "Kendi yetkini değiştiremezsin.");
        if (makeAdmin && u.isBlocked())
            throw new IllegalStateException("Askıdaki üyeye yetki verilemez.");
        if (u.isAdmin() == makeAdmin)
            throw new IllegalStateException(makeAdmin ? "Üye zaten yönetici." : "Üye zaten yönetici değil.");
        if (!makeAdmin && users.countByAdminTrue() <= 1)
            throw new IllegalStateException("Son yöneticinin yetkisi kaldırılamaz.");

        u.setAdmin(makeAdmin);
        users.save(u);
        notifications.notify(u, "hesap", makeAdmin
                ? "Hesabına yönetici yetkisi verildi."
                : "Hesabının yönetici yetkisi kaldırıldı.");
        return u;
    }

    /**
     * Üyeyi tamamen siler. Bağış, talep, istek veya takas kaydı olan üye silinemez;
     * geçmiş kayıtların bütünlüğü için bu durumda askıya alma kullanılır.
     */
    @Transactional
    public void deleteUser(User admin, Long userId) {
        User u = target(admin, userId, "Kendi hesabını silemezsin.");
        if (u.isAdmin())
            throw new IllegalStateException("Yönetici silinemez; önce yetkisini kaldır.");
        if (activityCount(u) > 0)
            throw new IllegalStateException("Bu üyenin kayıtları var; silmek yerine askıya al.");

        deleteDocument(u.getDocumentPath());
        notificationRepo.deleteByUser(u);
        // Sohbetler ve mesajları da gitmeli; yoksa yabancı anahtar bağı kalır
        conversations.findMine(u).forEach(c -> {
            messages.deleteByConversation(c);
            conversations.delete(c);
        });
        users.delete(u);
    }

    /** Üyenin sistemdeki toplam kayıt sayısı (silme güvenliği için). */
    public long activityCount(User u) {
        return donations.countByDonor(u)
                + claims.countByStudent(u)
                + requests.countByStudent(u)
                + swapBooks.countByUser(u)
                + swapOffers.countByFromUserOrToUser(u, u);
    }

    private User target(User admin, Long userId, String selfMessage) {
        User u = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Üye bulunamadı."));
        if (admin != null && admin.getId().equals(u.getId()))
            throw new IllegalStateException(selfMessage);
        return u;
    }

    // ---------- İçerik moderasyonu ----------

    public List<Donation> openDonations() {
        return donations.findOpenWithDetails(DonationStatus.OPEN);
    }

    public List<BookRequest> openRequests() {
        return requests.findByStatusWithDetails(RequestStatus.OPEN);
    }

    public List<SwapBook> openSwapBooks() {
        return swapBooks.findByStatusWithDetails(SwapBookStatus.OPEN);
    }

    /** Bağış ilanını yayından kaldırır; verilmiş talepler bozulmasın diye kayıt silinmez. */
    @Transactional
    public void removeDonation(Long id, String reason) {
        Donation d = donations.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("Bağış bulunamadı."));
        if (d.getStatus() == DonationStatus.CLOSED)
            throw new IllegalStateException("Bu bağış zaten kapalı.");

        d.setStatus(DonationStatus.CLOSED);
        donations.save(d);
        notifications.notify(d.getDonor(), "moderasyon",
                "\"" + d.getBook().getTitle() + "\" bağışın yönetim tarafından yayından kaldırıldı."
                        + suffix(reason));
    }

    /** Açık isteği kaldırır; henüz kimse karşılamadığı için kayıt tamamen silinir. */
    @Transactional
    public void removeRequest(Long id, String reason) {
        BookRequest r = requests.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));
        if (r.getStatus() != RequestStatus.OPEN)
            throw new IllegalStateException("Yalnızca karşılanmamış istekler kaldırılabilir.");

        User student = r.getStudent();
        String title = r.getBook().getTitle();
        requests.delete(r);
        notifications.notify(student, "moderasyon",
                "\"" + title + "\" isteğin yönetim tarafından kaldırıldı." + suffix(reason));
    }

    /** Takas ilanını kapatır; bekleyen teklifi varsa önce onlar sonuçlandırılmalı. */
    @Transactional
    public void removeSwapBook(Long id, String reason) {
        SwapBook s = swapBooks.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("Takas ilanı bulunamadı."));
        if (s.getStatus() == SwapBookStatus.CLOSED)
            throw new IllegalStateException("Bu ilan zaten kapalı.");
        long acik = swapOffers.countByBookAndStatuses(s, List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED));
        if (acik > 0)
            throw new IllegalStateException("İlana bağlı " + acik + " açık teklif var; önce onlar sonuçlanmalı.");

        s.setStatus(SwapBookStatus.CLOSED);
        swapBooks.save(s);
        notifications.notify(s.getUser(), "moderasyon",
                "\"" + s.getBook().getTitle() + "\" takas ilanın yönetim tarafından kaldırıldı." + suffix(reason));
    }

    private static String suffix(String reason) {
        return reason == null || reason.isBlank() ? "" : " Gerekçe: " + reason.trim();
    }
}
