package app.kitappla.web;

import app.kitappla.security.AppUserDetails;
import app.kitappla.service.DonationService;
import app.kitappla.service.MeetingRequest;
import app.kitappla.service.RequestService;
import app.kitappla.service.SwapService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Kampüs içi buluşma ayarlama. Bağış, istek ve takas akışlarının üçü de
 * aynı formu kullanır; yalnızca hedef kayıt türü değişir.
 * <p>
 * Elden teslimde buluşmayı <b>iki taraf da</b> ayarlayabilir; bu yüzden hangi
 * sayfadan gelindiği {@code geri} ile taşınır. Değer serbest bırakılmaz, bilinen
 * sayfalar listesinden doğrulanır (açık yönlendirme açığı olmasın).
 */
@Controller
public class MeetingController {

    private final DonationService donationService;
    private final RequestService requestService;
    private final SwapService swapService;

    public MeetingController(DonationService donationService, RequestService requestService,
                             SwapService swapService) {
        this.donationService = donationService;
        this.requestService = requestService;
        this.swapService = swapService;
    }

    @PostMapping("/bulusma/bagis/{claimId}")
    public String bagis(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long claimId,
                        @RequestParam(required = false) Long pointId,
                        @RequestParam(required = false) String note,
                        @RequestParam(required = false) String geri,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, hedef(geri, "/aldiklarim"),
                () -> donationService.arrange(claimId, principal.getUser(), request(pointId, note, at)));
    }

    @PostMapping("/bulusma/istek/{requestId}")
    public String istek(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long requestId,
                        @RequestParam(required = false) Long pointId,
                        @RequestParam(required = false) String note,
                        @RequestParam(required = false) String geri,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, hedef(geri, "/isteklerim"),
                () -> requestService.arrange(requestId, principal.getUser(), request(pointId, note, at)));
    }

    @PostMapping("/bulusma/takas/{offerId}")
    public String takas(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long offerId,
                        @RequestParam(required = false) Long pointId,
                        @RequestParam(required = false) String note,
                        @RequestParam(required = false) String geri,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, hedef(geri, "/takas/takaslarim"),
                () -> swapService.arrange(offerId, principal.getUser(), request(pointId, note, at)));
    }

    // ---------- Gelinmedi bildirimi ----------

    @PostMapping("/gelmedi/bagis/{claimId}")
    public String bagisGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long claimId,
                               @RequestParam(required = false) String geri, RedirectAttributes ra) {
        return gelmedi(ra, hedef(geri, "/aldiklarim"),
                () -> donationService.noShow(claimId, principal.getUser()));
    }

    @PostMapping("/gelmedi/istek/{requestId}")
    public String istekGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long requestId,
                               @RequestParam(required = false) String geri, RedirectAttributes ra) {
        return gelmedi(ra, hedef(geri, "/isteklerim"),
                () -> requestService.noShow(requestId, principal.getUser()));
    }

    @PostMapping("/gelmedi/takas/{offerId}")
    public String takasGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long offerId,
                               @RequestParam(required = false) String geri, RedirectAttributes ra) {
        return gelmedi(ra, hedef(geri, "/takas/takaslarim"),
                () -> swapService.noShow(offerId, principal.getUser()));
    }

    /** Bilinen sayfalar dışına yönlendirilmez; tanınmayan değer varsayılana düşer. */
    private static String hedef(String geri, String varsayilan) {
        if (geri == null || geri.isBlank()) return varsayilan;
        String g = geri.trim();
        boolean bilinen = List.of("/aldiklarim", "/bagislarim", "/isteklerim",
                        "/karsiladiklarim", "/takas/takaslarim").contains(g)
                || g.startsWith("/takas/teklifler/");
        return bilinen ? g : varsayilan;
    }

    private String gelmedi(RedirectAttributes ra, String target, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("basari", "Gelinmedi bildirimin kaydedildi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + target;
    }

    /** Form yerel saat gönderir; sunucunun saat diliminde yorumlanır. */
    private static MeetingRequest request(Long pointId, String note, LocalDateTime at) {
        return new MeetingRequest(pointId, note,
                at == null ? null : at.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String run(RedirectAttributes ra, String target, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("basari", "Buluşma kaydedildi ve karşı tarafa bildirildi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + target;
    }
}
