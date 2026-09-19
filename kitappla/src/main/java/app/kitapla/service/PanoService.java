package app.kitapla.service;

import app.kitapla.domain.ClaimStatus;
import app.kitapla.domain.OfferStatus;
import app.kitapla.domain.RequestStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.SwapOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Panodaki "Bekleyen işlerin" sayıları: üyenin taraf olduğu ve bir adım bekleyen süreçler.
 * Her sayı tek bir count sorgusudur; kayıtlar yüklenmez.
 */
@Service
public class PanoService {

    /** Buluşma ya da teslim bekleyen talep durumları (kargolanmış olan da teslim onayı bekler). */
    private static final List<ClaimStatus> SUREN_TALEP =
            List.of(ClaimStatus.MATCHED, ClaimStatus.ARRANGED, ClaimStatus.SHIPPED);
    private static final List<RequestStatus> SUREN_ISTEK =
            List.of(RequestStatus.FULFILLED, RequestStatus.ARRANGED, RequestStatus.SHIPPED);

    public record Bekleyenler(long gelenTeklif, long surenTakas, long aldiklarim,
                              long bagislarim, long isteklerim, long karsiladiklarim) {
        public long toplam() {
            return gelenTeklif + surenTakas + aldiklarim + bagislarim + isteklerim + karsiladiklarim;
        }
    }

    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;

    public PanoService(ClaimRepository claims, BookRequestRepository requests, SwapOfferRepository offers) {
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
    }

    @Transactional(readOnly = true)
    public Bekleyenler bekleyenler(User uye) {
        return new Bekleyenler(
                offers.countByToUserAndStatus(uye, OfferStatus.PENDING),
                offers.countTarafOlduguByStatus(uye, OfferStatus.ACCEPTED),
                claims.countByStudentAndStatusIn(uye, SUREN_TALEP),
                claims.countByDonorAndStatusIn(uye, SUREN_TALEP),
                requests.countByStudentAndStatusIn(uye, SUREN_ISTEK),
                requests.countByFulfilledByAndStatusIn(uye, SUREN_ISTEK));
    }
}
