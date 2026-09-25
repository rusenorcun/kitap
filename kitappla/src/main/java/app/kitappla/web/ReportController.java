package app.kitappla.web;

import app.kitappla.domain.ReportKind;
import app.kitappla.domain.ReportReason;
import app.kitappla.security.AppUserDetails;
import app.kitappla.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Üyelerin şikâyet göndermesi ve kendi şikâyetlerini takip etmesi.
 * <p>
 * Şikâyet gönderildikten sonra iş bitmez: kullanıcı durumu görebilmeli ve
 * gerekirse yönetimle aynı şikâyet üzerinden yazışabilmelidir. Bu yüzden
 * "Şikâyetlerim" listesi ve oradan açılan destek sohbeti bu sınıfta durur.
 */
@Controller
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Kullanıcının gönderdiği şikâyetler ve sonuçları. */
    @GetMapping("/sikayetlerim")
    public String sikayetlerim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("sikayetler", reports.mine(principal.getUser()));
        return "sikayetlerim";
    }

    @GetMapping("/sikayet/{kind}/{refId}")
    public String form(@AuthenticationPrincipal AppUserDetails principal,
                       @PathVariable String kind, @PathVariable Long refId,
                       @RequestParam(required = false) String geri, Model model,
                       RedirectAttributes ra) {
        ReportKind rKind = ReportKind.of(kind);
        if (rKind == null) {
            ra.addFlashAttribute("hata", "Geçersiz şikâyet türü.");
            return "redirect:" + icAdres(geri);
        }
        try {
            reports.validateCanReport(principal.getUser(), rKind, refId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:" + icAdres(geri);
        }
        model.addAttribute("tur", kind);
        model.addAttribute("turAdi", rKind.getEtiket());
        model.addAttribute("refId", refId);
        model.addAttribute("gerekceler", ReportReason.values());
        model.addAttribute("geri", icAdres(geri));
        return "sikayet";
    }

    /**
     * {@code geri} bağlantıdan gelir; doğrulanmadan yönlendirilirse dış siteye
     * taşınabilir. Yalnızca tek eğik çizgiyle başlayan, şema içermeyen uygulama
     * içi yollar kabul edilir ({@code //evil.example} ve {@code https://…} elenir).
     */
    static String icAdres(String geri) {
        if (geri == null) return "/panom";
        String g = geri.trim();
        // Tarayıcılar adresteki sekme/satır sonlarını atar: "/<TAB>/evil.example" Location
        // başlığında "//evil.example" olur. Bu yüzden boşluk ve denetim karakteri hiç kabul edilmez.
        if (g.isEmpty() || !g.startsWith("/") || g.startsWith("//")
                || g.contains(":") || g.contains("\\")
                || g.chars().anyMatch(ch -> ch <= 0x20 || ch == 0x7f)) return "/panom";
        return g;
    }

    @PostMapping("/sikayet/{kind}/{refId}")
    public String gonder(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable String kind, @PathVariable Long refId,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String geri,
                         RedirectAttributes ra) {
        String hedef = icAdres(geri);
        ReportKind rKind = ReportKind.of(kind);
        if (rKind == null) {
            ra.addFlashAttribute("hata", "Geçersiz şikâyet türü.");
            return "redirect:" + hedef;
        }

        ReportReason rReason = ReportReason.of(reason);
        if (rReason == null) {
            ra.addFlashAttribute("hata", "Lütfen geçerli bir şikâyet gerekçesi seçin.");
            return "redirect:" + hedef;
        }

        try {
            var r = reports.create(principal.getUser(),
                    rKind,
                    refId,
                    rReason,
                    note);
            ra.addFlashAttribute("basari",
                    "Şikâyetin yönetime iletildi (#" + r.getId() + "). İncelendiğinde bildirim alacaksın; "
                            + "durumunu Şikâyetlerim sayfasından takip edebilirsin.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + hedef;
    }
}
