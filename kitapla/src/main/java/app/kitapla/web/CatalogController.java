package app.kitapla.web;

import app.kitapla.domain.TargetLevel;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.ClaimEligibility;
import app.kitapla.service.DonationService;
import app.kitapla.service.DonationView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** Keşfet (bağış listesi) ve kitap detayı. Herkese açık. */
@Controller
public class CatalogController {

    private final DonationService donationService;

    public CatalogController(DonationService donationService) {
        this.donationService = donationService;
    }

    private static TargetLevel parseLevel(String level) {
        if (level == null || level.isBlank() || "hepsi".equalsIgnoreCase(level)) return null;
        try {
            return TargetLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<DonationView> load(String level, String q, boolean onlyAvailable) {
        return donationService.openDonations(
                new DonationService.Filter(parseLevel(level), q, null, onlyAvailable));
    }

    @GetMapping("/kesfet")
    public String kesfet(@RequestParam(required = false) String level,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false, defaultValue = "true") boolean available,
                         Model model) {
        model.addAttribute("donations", load(level, q, available));
        model.addAttribute("level", level == null ? "hepsi" : level);
        model.addAttribute("q", q);
        model.addAttribute("available", available);
        return "kesfet";
    }

    /** HTMX: yalnızca ızgara parçası döner (sayfa yenilenmez). */
    @GetMapping("/kesfet/liste")
    public String kesfetListe(@RequestParam(required = false) String level,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false, defaultValue = "true") boolean available,
                              Model model) {
        model.addAttribute("donations", load(level, q, available));
        return "kesfet :: grid";
    }

    @GetMapping("/kitap/{id}")
    public String kitapDetay(@PathVariable Long id, Model model) throws NoResourceFoundException {
        DonationView view = donationService.view(id)
                .orElseThrow(() -> new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/kitap/" + id));

        User user = CurrentUser.get();
        ClaimEligibility eligibility = donationService.eligibility(view, user);

        model.addAttribute("d", view);
        model.addAttribute("eligibility", eligibility);
        model.addAttribute("benzer", donationService.openDonations(
                        new DonationService.Filter(null, null, null, true)).stream()
                .filter(v -> !v.getId().equals(id))
                .limit(6).toList());
        return "kitap-detay";
    }

    /** Bağıştan kitap al. Kurallar DonationService'te; hata mesajı kullanıcıya gösterilir. */
    @PostMapping("/kitap/{id}/al")
    public String al(@PathVariable Long id, RedirectAttributes ra) {
        User user = CurrentUser.get();
        if (user == null) return "redirect:/login";
        try {
            donationService.claim(id, user);
            ra.addFlashAttribute("basari", "Kitap senin! Bağışçı kargoya verdiğinde haber vereceğiz.");
            return "redirect:/aldiklarim";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/kitap/" + id;
        }
    }
}
