package app.kitapla.service;

import app.kitapla.domain.Meeting;
import app.kitapla.domain.PickupPoint;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Buluşma bilgisinin doğrulanması ve yazılması. Bağış, istek ve takas
 * akışlarının üçü de aynı kuralları kullansın diye tek yerde toplandı.
 */
@Service
public class MeetingService {

    /** Geçmişe buluşma ayarlanmasın, ama saat farkı/gecikme için küçük bir pay bırak. */
    private static final Duration GECMIS_PAYI = Duration.ofMinutes(15);
    private static final Duration EN_UZAK = Duration.ofDays(60);

    private final PickupPointService points;

    public MeetingService(PickupPointService points) {
        this.points = points;
    }

    /** Girdiyi doğrulayıp buluşmayı doldurur. */
    public Meeting apply(Meeting meeting, MeetingRequest request) {
        if (request == null) throw new IllegalStateException("Buluşma bilgisi eksik.");

        PickupPoint point = null;
        if (request.pointId() != null) {
            point = points.findSelectable(request.pointId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Seçtiğin teslim noktası artık kullanılmıyor. Listeden başka bir nokta seç."));
        }

        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.isEmpty()) note = null;
        if (note != null && note.length() > 300) note = note.substring(0, 300);

        if (point == null && note == null)
            throw new IllegalStateException("Bir teslim noktası seç ya da buluşma yerini yaz.");

        Instant at = request.at();
        if (at == null)
            throw new IllegalStateException("Buluşma zamanını seç.");
        if (at.isBefore(Instant.now().minus(GECMIS_PAYI)))
            throw new IllegalStateException("Buluşma zamanı geçmişte olamaz.");
        if (at.isAfter(Instant.now().plus(EN_UZAK)))
            throw new IllegalStateException("Buluşma en fazla 60 gün sonrasına ayarlanabilir.");

        meeting.setPoint(point);
        meeting.setNote(note);
        meeting.setAt(at);
        meeting.setArrangedAt(Instant.now());
        return meeting;
    }

    /** Bildirim metinlerinde kullanılan özet. */
    public String summary(Meeting m) {
        String yer = m.getPlaceText();
        String zaman = m.getAtText();
        if (yer == null) return zaman == null ? "" : zaman;
        return zaman == null ? yer : yer + ", " + zaman;
    }
}
