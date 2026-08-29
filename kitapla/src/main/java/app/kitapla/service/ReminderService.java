package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.SwapOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Yaklaşan buluşmalar için hatırlatma bildirimi gönderir.
 * <p>
 * Her buluşma yalnızca bir kez hatırlatılır ({@code meeting.remindedAt}).
 * Görev sık çalışır ama yalnızca eşiğe girmiş ve hatırlatılmamış kayıtları
 * sorgular; boş geçen turlar veritabanına neredeyse hiç yük bindirmez.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;
    private final NotificationService notifications;
    private final MeetingService meetings;
    private final Duration onceden;

    public ReminderService(ClaimRepository claims, BookRequestRepository requests,
                           SwapOfferRepository offers, NotificationService notifications,
                           MeetingService meetings,
                           @Value("${kitapla.reminder.hours-before:3}") int saatOnce) {
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
        this.notifications = notifications;
        this.meetings = meetings;
        this.onceden = Duration.ofHours(saatOnce);
    }

    /** Varsayılan olarak 10 dakikada bir. */
    @Scheduled(fixedDelayString = "${kitapla.reminder.interval-ms:600000}",
               initialDelayString = "${kitapla.reminder.initial-delay-ms:60000}")
    public void hatirlat() {
        int gonderilen = sendDue();
        if (gonderilen > 0) log.info("{} buluşma hatırlatması gönderildi.", gonderilen);
    }

    /** Zamanı gelen hatırlatmaları gönderir; gönderilen bildirim sayısını döndürür. */
    @Transactional
    public int sendDue() {
        Instant simdi = Instant.now();
        Instant esik = simdi.plus(onceden);
        int n = 0;

        for (Claim c : claims.findYaklasanBulusmalar(ClaimStatus.ARRANGED, simdi, esik)) {
            String metin = "\"" + c.getDonation().getBook().getTitle() + "\" buluşman yaklaşıyor: "
                    + meetings.summary(c.getMeeting());
            notifications.notify(c.getStudent(), "bulusma_hatirlatma", metin);
            notifications.notify(c.getDonation().getDonor(), "bulusma_hatirlatma", metin);
            c.getMeeting().setRemindedAt(simdi);
            claims.save(c);
            n += 2;
        }

        for (BookRequest r : requests.findYaklasanBulusmalar(RequestStatus.ARRANGED, simdi, esik)) {
            String metin = "\"" + r.getBook().getTitle() + "\" buluşman yaklaşıyor: "
                    + meetings.summary(r.getMeeting());
            notifications.notify(r.getStudent(), "bulusma_hatirlatma", metin);
            if (r.getFulfilledBy() != null)
                notifications.notify(r.getFulfilledBy(), "bulusma_hatirlatma", metin);
            r.getMeeting().setRemindedAt(simdi);
            requests.save(r);
            n += 2;
        }

        for (SwapOffer o : offers.findYaklasanBulusmalar(OfferStatus.ACCEPTED, simdi, esik)) {
            String metin = "Takas buluşman yaklaşıyor: " + meetings.summary(o.getMeeting());
            notifications.notify(o.getFromUser(), "bulusma_hatirlatma", metin);
            notifications.notify(o.getToUser(), "bulusma_hatirlatma", metin);
            o.getMeeting().setRemindedAt(simdi);
            offers.save(o);
            n += 2;
        }

        return n;
    }
}
