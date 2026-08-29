package app.kitapla.web;

import app.kitapla.domain.ReportKind;
import app.kitapla.domain.ReportReason;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Üyelerin şikâyet göndermesi. */
@Controller
@RequestMapping("/sikayet")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/{kind}/{refId}")
    public String form(@PathVariable String kind, @PathVariable Long refId,
                       @RequestParam(required = false) String geri, Model model) {
        model.addAttribute("tur", kind);
        model.addAttribute("refId", refId);
        model.addAttribute("gerekceler", ReportReason.values());
        model.addAttribute("geri", geri == null || geri.isBlank() ? "/panom" : geri);
        return "sikayet";
    }

    @PostMapping("/{kind}/{refId}")
    public String gonder(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable String kind, @PathVariable Long refId,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String geri,
                         RedirectAttributes ra) {
        String hedef = (geri == null || geri.isBlank()) ? "/panom" : geri;
        try {
            reports.create(principal.getUser(),
                    ReportKind.valueOf(kind.toUpperCase()),
                    refId,
                    reason == null || reason.isBlank() ? null : ReportReason.valueOf(reason),
                    note);
            ra.addFlashAttribute("basari",
                    "Şikâyetin yönetime iletildi. İncelendiğinde bildirim alacaksın.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + hedef;
    }
}
