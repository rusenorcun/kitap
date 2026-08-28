package app.kitapla.service;

import app.kitapla.config.Features;
import app.kitapla.domain.*;
import app.kitapla.repo.SwapBookRepository;
import app.kitapla.repo.SwapOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Üyeler arası kitap takası. Bağış kotasından bağımsızdır (eşit değişim).
 * Adresler yalnızca teklif KABUL edildikten sonra iki tarafa açılır.
 */
@Service
public class SwapService {

    private static final List<OfferStatus> LIVE = List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED);

    private final Features features;
    private final SwapBookRepository swapBooks;
    private final SwapOfferRepository offers;
    private final NotificationService notifications;

    public SwapService(Features features, SwapBookRepository swapBooks, SwapOfferRepository offers,
                       NotificationService notifications) {
        this.features = features;
        this.swapBooks = swapBooks;
        this.offers = offers;
        this.notifications = notifications;
    }

    // ---------- Takas kitapları ----------

    /** Kitabımı takasa aç. */
    @Transactional
    public SwapBook open(User user, Book book, String note) {
        if (book == null) throw new IllegalStateException("Kitap seçilmedi.");
        if (features.isAddress() && (user.getAddress() == null || user.getAddress().isBlank()))
            throw new IllegalStateException("Takas için profilinden teslimat adresi eklemelisin.");
        if (swapBooks.findByUserAndBook_Id(user, book.getId()).isPresent())
            throw new IllegalStateException("Bu kitabı zaten takasa açtın.");

        SwapBook s = new SwapBook();
        s.setUser(user);
        s.setBook(book);
        s.setNote(note);
        return swapBooks.save(s);
    }

    @Transactional(readOnly = true)
    public List<SwapBook> discover(User me, String query) {
        String q = query == null ? null : query.trim().toLowerCase();
        return swapBooks.findOpenOfOthers(SwapBookStatus.OPEN, me).stream()
                .filter(s -> {
                    if (q == null || q.isBlank()) return true;
                    String t = s.getBook().getTitle() == null ? "" : s.getBook().getTitle().toLowerCase();
                    String a = s.getBook().getAuthor() == null ? "" : s.getBook().getAuthor().toLowerCase();
                    return t.contains(q) || a.contains(q);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapBook> myBooks(User user) {
        return swapBooks.findByUserWithDetails(user);
    }

    @Transactional(readOnly = true)
    public List<SwapBook> myOpenBooks(User user) {
        return swapBooks.findByUserAndStatusWithDetails(user, SwapBookStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Optional<SwapBook> viewBook(Long id) {
        return swapBooks.findByIdWithDetails(id);
    }

    /** Takas kitabını aç/kapat. */
    @Transactional
    public void setStatus(Long swapBookId, User user, SwapBookStatus status) {
        SwapBook s = ownBook(swapBookId, user);
        if (status == SwapBookStatus.CLOSED && offers.countByBookAndStatuses(s, List.of(OfferStatus.ACCEPTED)) > 0)
            throw new IllegalStateException("Kabul edilmiş bir takasa bağlı kitap kapatılamaz.");
        s.setStatus(status);
        swapBooks.save(s);
    }

    /** Takastan kaldır. */
    @Transactional
    public void removeBook(Long swapBookId, User user) {
        SwapBook s = ownBook(swapBookId, user);
        if (offers.countByBookAndStatuses(s, LIVE) > 0)
            throw new IllegalStateException("Bekleyen ya da kabul edilmiş teklifi olan kitap kaldırılamaz.");
        swapBooks.delete(s);
    }

    private SwapBook ownBook(Long id, User user) {
        SwapBook s = swapBooks.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("Takas kitabı bulunamadı."));
        if (!s.getUser().getId().equals(user.getId()))
            throw new IllegalStateException("Bu kitap sana ait değil.");
        return s;
    }

    // ---------- Teklifler ----------

    /** Kendi kitabımı, hedef kitabın sahibine teklif et. */
    @Transactional
    public SwapOffer offer(Long targetBookId, Long offeredBookId, User me, String message) {
        SwapBook target = swapBooks.findByIdWithDetails(targetBookId)
                .orElseThrow(() -> new IllegalStateException("Hedef kitap bulunamadı."));
        if (target.getStatus() != SwapBookStatus.OPEN)
            throw new IllegalStateException("Bu kitap artık takasa açık değil.");
        if (target.getUser().getId().equals(me.getId()))
            throw new IllegalStateException("Kendi kitabına teklif veremezsin.");
        if (features.isAddress() && (me.getAddress() == null || me.getAddress().isBlank()))
            throw new IllegalStateException("Takas için profilinden teslimat adresi eklemelisin.");

        SwapBook offered = swapBooks.findByIdWithDetails(offeredBookId)
                .orElseThrow(() -> new IllegalStateException("Teklif edeceğin kitap bulunamadı."));
        if (!offered.getUser().getId().equals(me.getId()))
            throw new IllegalStateException("Yalnızca kendi kitabını teklif edebilirsin.");
        if (offered.getStatus() != SwapBookStatus.OPEN)
            throw new IllegalStateException("Teklif edeceğin kitap takasa açık olmalı.");

        if (offers.existsByFromUserAndTargetSwapBookAndStatus(me, target, OfferStatus.PENDING))
            throw new IllegalStateException("Bu kitap için zaten bekleyen bir teklifin var.");

        SwapOffer o = new SwapOffer();
        o.setFromUser(me);
        o.setToUser(target.getUser());
        o.setOfferedSwapBook(offered);
        o.setTargetSwapBook(target);
        o.setMessage(message);
        o = offers.save(o);

        notifications.notify(target.getUser(), "swap_offer",
                me.getName() + " sana takas teklif etti: \"" + offered.getBook().getTitle()
                        + "\" ↔ \"" + target.getBook().getTitle() + "\"");
        return o;
    }

    @Transactional(readOnly = true)
    public List<SwapOffer> incoming(User user) { return offers.findIncoming(user); }

    @Transactional(readOnly = true)
    public List<SwapOffer> outgoing(User user) { return offers.findOutgoing(user); }

    /** Teklifi kabul et: iki kitap kapanır, rakip teklifler reddedilir, adresler açılır. */
    @Transactional
    public SwapOffer accept(Long offerId, User me) {
        SwapOffer o = offers.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalStateException("Teklif bulunamadı."));
        if (!o.getToUser().getId().equals(me.getId()))
            throw new IllegalStateException("Bu teklif sana gelmedi.");
        if (o.getStatus() != OfferStatus.PENDING)
            throw new IllegalStateException("Bu teklif zaten yanıtlanmış.");

        o.setStatus(OfferStatus.ACCEPTED);
        o.setDecidedAt(Instant.now());
        offers.save(o);

        SwapBook a = o.getOfferedSwapBook();
        SwapBook b = o.getTargetSwapBook();
        a.setStatus(SwapBookStatus.CLOSED);
        b.setStatus(SwapBookStatus.CLOSED);
        swapBooks.save(a);
        swapBooks.save(b);

        // Aynı kitaplara bağlı diğer bekleyen teklifleri reddet
        for (SwapOffer other : offers.findCompeting(OfferStatus.PENDING, o.getId(), List.of(a, b))) {
            other.setStatus(OfferStatus.REJECTED);
            other.setDecidedAt(Instant.now());
            offers.save(other);
            notifications.notify(other.getFromUser(), "swap_rejected",
                    "Takas teklifin reddedildi: \"" + other.getTargetSwapBook().getBook().getTitle() + "\" başka biriyle takaslandı.");
        }

        notifications.notify(o.getFromUser(), "swap_accepted",
                me.getName() + " takas teklifini kabul etti. Adresler paylaşıldı; kitabı kargolayabilirsin.");
        return o;
    }

    /** Teklifi reddet (hedef sahibi). */
    @Transactional
    public void reject(Long offerId, User me) {
        SwapOffer o = pendingOffer(offerId);
        if (!o.getToUser().getId().equals(me.getId()))
            throw new IllegalStateException("Bu teklif sana gelmedi.");
        o.setStatus(OfferStatus.REJECTED);
        o.setDecidedAt(Instant.now());
        offers.save(o);
        notifications.notify(o.getFromUser(), "swap_rejected",
                "Takas teklifin reddedildi: \"" + o.getTargetSwapBook().getBook().getTitle() + "\".");
    }

    /** Teklifi geri çek (teklif eden). */
    @Transactional
    public void cancel(Long offerId, User me) {
        SwapOffer o = pendingOffer(offerId);
        if (!o.getFromUser().getId().equals(me.getId()))
            throw new IllegalStateException("Bu teklif sana ait değil.");
        o.setStatus(OfferStatus.CANCELLED);
        o.setDecidedAt(Instant.now());
        offers.save(o);
    }

    private SwapOffer pendingOffer(Long id) {
        SwapOffer o = offers.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("Teklif bulunamadı."));
        if (o.getStatus() != OfferStatus.PENDING)
            throw new IllegalStateException("Bu teklif zaten yanıtlanmış.");
        return o;
    }

    /** Kargoya verdim. İki taraf da verince takas tamamlanır. */
    @Transactional
    public void ship(Long offerId, User me) {
        SwapOffer o = offers.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalStateException("Teklif bulunamadı."));
        if (o.getStatus() != OfferStatus.ACCEPTED)
            throw new IllegalStateException("Yalnızca kabul edilmiş takas kargolanabilir.");

        boolean isFrom = o.getFromUser().getId().equals(me.getId());
        boolean isTo = o.getToUser().getId().equals(me.getId());
        if (!isFrom && !isTo) throw new IllegalStateException("Bu takas sana ait değil.");

        if (isFrom) {
            if (o.getFromShippedAt() != null) throw new IllegalStateException("Zaten kargoya verdin.");
            o.setFromShippedAt(Instant.now());
        } else {
            if (o.getToShippedAt() != null) throw new IllegalStateException("Zaten kargoya verdin.");
            o.setToShippedAt(Instant.now());
        }

        User other = isFrom ? o.getToUser() : o.getFromUser();
        notifications.notify(other, "swap_shipped", me.getName() + " takas kitabını kargoya verdi.");

        if (o.getFromShippedAt() != null && o.getToShippedAt() != null) {
            o.setStatus(OfferStatus.COMPLETED);
            o.setDecidedAt(Instant.now());
            notifications.notify(o.getFromUser(), "swap_completed", "Takas tamamlandı. İyi okumalar!");
            notifications.notify(o.getToUser(), "swap_completed", "Takas tamamlandı. İyi okumalar!");
        }
        offers.save(o);
    }

    /** Adresler yalnızca kabul edilmiş/tamamlanmış takasta paylaşılır. */
    public boolean addressVisible(SwapOffer o) {
        return o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.COMPLETED;
    }

    /** Bu kullanıcı bu teklifte kargoya verdi mi? */
    public boolean hasShipped(SwapOffer o, User me) {
        return o.getFromUser().getId().equals(me.getId()) ? o.getFromShippedAt() != null : o.getToShippedAt() != null;
    }

    /** Teklifte karşı taraf. */
    public User counterpart(SwapOffer o, User me) {
        return o.getFromUser().getId().equals(me.getId()) ? o.getToUser() : o.getFromUser();
    }

    /** Bu kullanıcının bu takasta vereceği kitap. */
    public SwapBook myBookIn(SwapOffer o, User me) {
        return o.getFromUser().getId().equals(me.getId()) ? o.getOfferedSwapBook() : o.getTargetSwapBook();
    }

    /** Bu kullanıcının bu takasta alacağı kitap. */
    public SwapBook theirBookIn(SwapOffer o, User me) {
        return o.getFromUser().getId().equals(me.getId()) ? o.getTargetSwapBook() : o.getOfferedSwapBook();
    }
}
