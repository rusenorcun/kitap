package app.kitapla.web;

import app.kitapla.security.AppUserDetails;
import app.kitapla.service.DonationService;
import app.kitapla.service.MeetingRequest;
import app.kitapla.service.RequestService;
import app.kitapla.service.SwapService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Kampüs içi buluşma ayarlama. Bağış, istek ve takas akışlarının üçü de
 * aynı formu kullanır; yalnızca hedef kayıt türü değişir.
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
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, "/aldiklarim",
                () -> donationService.arrange(claimId, principal.getUser(), request(pointId, note, at)));
    }

    @PostMapping("/bulusma/istek/{requestId}")
    public String istek(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long requestId,
                        @RequestParam(required = false) Long pointId,
                        @RequestParam(required = false) String note,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, "/isteklerim",
                () -> requestService.arrange(requestId, principal.getUser(), request(pointId, note, at)));
    }

    @PostMapping("/bulusma/takas/{offerId}")
    public String takas(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long offerId,
                        @RequestParam(required = false) Long pointId,
                        @RequestParam(required = false) String note,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                        RedirectAttributes ra) {
        return run(ra, "/takas/takaslarim",
                () -> swapService.arrange(offerId, principal.getUser(), request(pointId, note, at)));
    }

    // ---------- Gelinmedi bildirimi ----------

    @PostMapping("/gelmedi/bagis/{claimId}")
    public String bagisGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long claimId, RedirectAttributes ra) {
        return gelmedi(ra, "/aldiklarim", () -> donationService.noShow(claimId, principal.getUser()));
    }

    @PostMapping("/gelmedi/istek/{requestId}")
    public String istekGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long requestId, RedirectAttributes ra) {
        return gelmedi(ra, "/isteklerim", () -> requestService.noShow(requestId, principal.getUser()));
    }

    @PostMapping("/gelmedi/takas/{offerId}")
    public String takasGelmedi(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long offerId, RedirectAttributes ra) {
        return gelmedi(ra, "/takas/takaslarim", () -> swapService.noShow(offerId, principal.getUser()));
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
