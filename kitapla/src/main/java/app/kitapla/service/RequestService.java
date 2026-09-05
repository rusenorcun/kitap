package app.kitapla.service;

import app.kitapla.config.Features;
import app.kitapla.domain.*;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Kitap istekleri: bir üye ihtiyacı olan kitabı listeler, bir başkası karşılar.
 * Karşılanan istek alıcının kotasından düşer; adres yalnızca karşılayana açılır.
 */
@Service
public class RequestService {

    /** Aynı anda açık tutulabilecek istek sayısı (suistimali önler). */
    public static final int MAX_OPEN_REQUESTS = 5;

    private final Features features;
    private final MeetingService meetings;
    private final UserRepository users;
    private final BookRequestRepository requests;
    private final QuotaService quotaService;
    private final NotificationService notifications;

    public RequestService(Features features, MeetingService meetings, UserRepository users,
                          BookRequestRepository requests, QuotaService quotaService,
                          NotificationService notifications) {
        this.features = features;
        this.meetings = meetings;
        this.users = users;
        this.requests = requests;
        this.quotaService = quotaService;
        this.notifications = notifications;
    }

    // ---------- Okuma ----------

    @Transactional(readOnly = true)
    public List<BookRequest> openRequests(String query) {
        String q = query == null ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
        return requests.findByStatusWithDetails(RequestStatus.OPEN).stream()
                .filter(r -> {
                    if (q == null || q.isBlank()) return true;
                    String t = r.getBook().getTitle() == null ? "" : r.getBook().getTitle().toLowerCase(java.util.Locale.ROOT);
                    String a = r.getBook().getAuthor() == null ? "" : r.getBook().getAuthor().toLowerCase(java.util.Locale.ROOT);
                    return t.contains(q) || a.contains(q);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookRequest> myRequests(User user) {
        return requests.findByStudentWithDetails(user);
    }

    /** Karşıladığım istekler — teslimat adresi bu listede gösterilir. */
    @Transactional(readOnly = true)
    public List<BookRequest> fulfilledByMe(User user) {
        return requests.findByFulfilledByWithDetails(user);
    }

    @Transactional(readOnly = true)
    public Optional<BookRequest> view(Long id) {
        return requests.findByIdWithDetails(id);
    }

    // ---------- Yazma ----------

    @Transactional
    public BookRequest create(User user, Book book, String description) {
        if (book == null) throw new IllegalStateException("Kitap seçilmedi.");
        if (features.isAddress() && (user.getAddress() == null || user.getAddress().isBlank()))
            throw new IllegalStateException("İstek oluşturmadan önce profilinden teslimat adresi eklemelisin.");

        long open = requests.countByStudentAndStatus(user, RequestStatus.OPEN);
        if (open >= MAX_OPEN_REQUESTS)
            throw new IllegalStateException("Aynı anda en fazla " + MAX_OPEN_REQUESTS
                    + " açık isteğin olabilir. Önce mevcut isteklerinden birini kaldır.");

        boolean duplicate = requests.findByStudentWithDetails(user).stream()
                .anyMatch(r -> r.getStatus() == RequestStatus.OPEN && r.getBook().getId().equals(book.getId()));
        if (duplicate) throw new IllegalStateException("Bu kitap için zaten açık bir isteğin var.");

        BookRequest r = new BookRequest();
        r.setStudent(user);
        r.setBook(book);
        r.setDescription(description);
        return requests.save(r);
    }

    /** İsteği karşıla. Kota, isteği OLUŞTURAN kişiye göre kontrol edilir. */
    @Transactional
    public BookRequest fulfill(Long requestId, User fulfiller, DonationSource source) {
        BookRequest r = requests.findByIdWithDetailsForUpdate(requestId)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));

        if (r.getStatus() != RequestStatus.OPEN)
            throw new IllegalStateException("Bu istek zaten karşılanmış.");
        if (r.getStudent().getId().equals(fulfiller.getId()))
            throw new IllegalStateException("Kendi isteğini karşılayamazsın.");
        if (fulfiller.isBlocked())
            throw new IllegalStateException("Hesabın engellenmiş.");

        String quotaReason = quotaService.cannotReceiveReason(r.getStudent());
        if (quotaReason != null)
            throw new IllegalStateException("İsteği açan kişinin kotası dolu: " + quotaReason);

        r.setStatus(RequestStatus.FULFILLED);
        r.setSource(source == null ? DonationSource.PURCHASE : source);
        r.setFulfilledBy(fulfiller);
        r.setFulfilledAt(Instant.now());
        requests.save(r);

        notifications.notify(r.getStudent(), "request_fulfilled",
                "\"" + r.getBook().getTitle() + "\" isteğin " + fulfiller.getName() + " tarafından karşılandı.");
        return r;
    }

    /** Karşılayan kargoya verdi. */
    @Transactional
    public void ship(Long requestId, User fulfiller) {
        BookRequest r = ownFulfilled(requestId, fulfiller);
        if (r.getStatus() != RequestStatus.FULFILLED)
            throw new IllegalStateException("Bu istek kargo aşamasında değil.");
        r.setStatus(RequestStatus.SHIPPED);
        r.setShippedAt(Instant.now());
        requests.save(r);
        notifications.notify(r.getStudent(), "request_shipped",
                "\"" + r.getBook().getTitle() + "\" kitabın kargoya verildi.");
    }

    /**
     * Buluşmayı ayarlar ya da günceller. İsteği açan da karşılayan da yapabilir.
     */
    @Transactional
    public BookRequest arrange(Long requestId, User user, MeetingRequest request) {
        BookRequest r = requests.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));

        boolean isteyen = r.getStudent().getId().equals(user.getId());
        boolean karsilayan = r.getFulfilledBy() != null
                && r.getFulfilledBy().getId().equals(user.getId());
        if (!isteyen && !karsilayan)
            throw new IllegalStateException("Bu istek sana ait değil.");
        if (r.getStatus() == RequestStatus.OPEN)
            throw new IllegalStateException("Bu isteği henüz kimse karşılamadı.");
        if (r.getStatus() == RequestStatus.DELIVERED)
            throw new IllegalStateException("Bu kitap zaten teslim edildi.");

        meetings.apply(r.getMeeting(), request);
        r.setStatus(RequestStatus.ARRANGED);
        requests.save(r);

        User digeri = isteyen ? r.getFulfilledBy() : r.getStudent();
        notifications.notify(digeri, "meeting_arranged",
                "\"" + r.getBook().getTitle() + "\" için buluşma ayarlandı: "
                        + meetings.summary(r.getMeeting()));
        return r;
    }

    /**
     * Karşı taraf buluşmaya gelmedi. İstek yeniden açılmaz — isteyen kişi
     * hakkını kullanmış sayılır (kota cezası); karşılayan gelmediyse istek
     * yeniden açık hâle gelir ki başkası karşılayabilsin.
     */
    @Transactional
    public BookRequest noShow(Long requestId, User bildiren) {
        BookRequest r = requests.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));

        boolean isteyen = r.getStudent().getId().equals(bildiren.getId());
        boolean karsilayan = r.getFulfilledBy() != null
                && r.getFulfilledBy().getId().equals(bildiren.getId());
        if (!isteyen && !karsilayan) throw new IllegalStateException("Bu istek sana ait değil.");

        if (r.getStatus() == RequestStatus.DELIVERED)
            throw new IllegalStateException("Bu kitap zaten teslim edilmiş.");
        if (r.getStatus() == RequestStatus.NO_SHOW)
            throw new IllegalStateException("Bu kayıt zaten gelinmedi olarak işaretlenmiş.");
        if (!r.getMeeting().isArranged())
            throw new IllegalStateException("Önce bir buluşma ayarlanmış olmalı.");
        if (r.getMeeting().getAt() != null && Instant.now().isBefore(r.getMeeting().getAt()))
            throw new IllegalStateException("Buluşma saati daha gelmedi.");

        User gelmeyen = isteyen ? r.getFulfilledBy() : r.getStudent();
        String kitap = r.getBook().getTitle();

        if (isteyen) {
            // Karşılayan gelmedi: istek yeniden açılır, başkası karşılayabilsin
            r.setStatus(RequestStatus.OPEN);
            r.setFulfilledBy(null);
            r.setFulfilledAt(null);
            r.getMeeting().setArrangedAt(null);
        } else {
            // İsteyen gelmedi: kayıt kapanır, kota hakkı yanar
            r.setStatus(RequestStatus.NO_SHOW);
        }
        requests.save(r);

        if (gelmeyen != null) {
            gelmeyen.setNoShowCount(gelmeyen.getNoShowCount() + 1);
            users.save(gelmeyen);
            notifications.notify(gelmeyen, "gelmedi",
                    "\"" + kitap + "\" buluşmasına gelmediğin bildirildi. "
                            + "Tekrarlanırsa hesabın askıya alınabilir.");
        }
        notifications.notify(bildiren, "gelmedi_kayit",
                "\"" + kitap + "\" için gelinmedi bildirimin kaydedildi.");
        return r;
    }

    /** İsteği açan teslim aldı. */
    @Transactional
    public void deliver(Long requestId, User requester) {
        BookRequest r = ownRequest(requestId, requester);
        if (r.getStatus() != RequestStatus.FULFILLED && r.getStatus() != RequestStatus.SHIPPED
                && r.getStatus() != RequestStatus.ARRANGED)
            throw new IllegalStateException("Bu istek teslim aşamasında değil.");
        // Yüz yüze teslimde önce buluşma ayarlanmış olmalı
        if (features.isHandover() && !features.isShipping() && r.getStatus() == RequestStatus.FULFILLED)
            throw new IllegalStateException("Önce buluşma ayarlayın, sonra teslimi onaylayın.");
        r.setStatus(RequestStatus.DELIVERED);
        r.setDeliveredAt(Instant.now());
        requests.save(r);
        if (r.getFulfilledBy() != null) {
            notifications.notify(r.getFulfilledBy(), "request_delivered",
                    requester.getName() + ", \"" + r.getBook().getTitle() + "\" kitabını teslim aldı.");
        }
    }

    /** İsteği açan teşekkür eder. */
    @Transactional
    public void thank(Long requestId, User requester, String message) {
        BookRequest r = ownRequest(requestId, requester);
        if (r.getStatus() != RequestStatus.DELIVERED)
            throw new IllegalStateException("Teşekkür notu yalnızca teslim aldığın kitaplar için gönderilebilir.");
        if (r.getFulfilledBy() == null) return;
        String note = (message == null || message.isBlank()) ? "" : " Notu: \"" + message.trim() + "\"";
        notifications.notify(r.getFulfilledBy(), "thank_you",
                requester.getName() + ", \"" + r.getBook().getTitle() + "\" için teşekkür etti." + note);
    }

    /** Açık isteği kaldır. */
    @Transactional
    public void delete(Long requestId, User requester) {
        BookRequest r = ownRequest(requestId, requester);
        if (r.getStatus() != RequestStatus.OPEN)
            throw new IllegalStateException("Karşılanmış bir istek kaldırılamaz.");
        requests.delete(r);
    }

    private BookRequest ownRequest(Long id, User user) {
        BookRequest r = requests.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));
        if (!r.getStudent().getId().equals(user.getId()))
            throw new IllegalStateException("Bu istek sana ait değil.");
        return r;
    }

    private BookRequest ownFulfilled(Long id, User user) {
        BookRequest r = requests.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));
        if (r.getFulfilledBy() == null || !r.getFulfilledBy().getId().equals(user.getId()))
            throw new IllegalStateException("Bu isteği sen karşılamadın.");
        return r;
    }
}
