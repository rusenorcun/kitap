package app.kitappla.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import app.kitappla.domain.TargetLevel;
import app.kitappla.domain.User;
import app.kitappla.security.CurrentUser;
import app.kitappla.service.ClaimEligibility;
import app.kitappla.service.DonationService;
import app.kitappla.service.DonationView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Keşfet (bağış listesi) ve kitap detayı. Herkese açık. */
@Controller
public class CatalogController {

    private final DonationService donationService;
    private final app.kitappla.config.Features features;

    public CatalogController(DonationService donationService, app.kitappla.config.Features features) {
        this.donationService = donationService;
        this.features = features;
    }

    private static TargetLevel parseLevel(String level) {
        if (level == null || level.isBlank() || "hepsi".equalsIgnoreCase(level)) return null;
        try {
            return TargetLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Page<DonationView> load(String level, String q, boolean onlyAvailable, int sayfa) {
        return donationService.openDonations(
                new DonationService.Filter(parseLevel(level), q, onlyAvailable),
                ListeSayfasi.of(sayfa));
    }

    /** Liste ve "daha fazla göster" bağlantısının ihtiyaç duyduğu model değerleri. */
    private void listeModeli(Model model, String level, String q, boolean available, int sayfa) {
        model.addAttribute("donations", load(level, q, available, sayfa));
        model.addAttribute("level", level == null ? "hepsi" : level);
        model.addAttribute("q", q);
        model.addAttribute("available", available);
    }

    @GetMapping("/kesfet")
    public String kesfet(@RequestParam(required = false) String level,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false, defaultValue = "true") boolean available,
                         @RequestParam(defaultValue = "0") int sayfa,
                         Model model) {
        listeModeli(model, level, q, available, sayfa);
        return "kesfet";
    }

    /**
     * HTMX: filtre değişince ızgaranın tamamı (sayfa 0), "daha fazla göster" ile
     * yalnızca sonraki sayfanın kartları döner.
     */
    @GetMapping("/kesfet/liste")
    public String kesfetListe(@RequestParam(required = false) String level,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false, defaultValue = "true") boolean available,
                              @RequestParam(defaultValue = "0") int sayfa,
                              Model model) {
        listeModeli(model, level, q, available, sayfa);
        return sayfa <= 0 ? "kesfet :: grid" : "kesfet :: kartlar";
    }

    @GetMapping("/kitap/{id}")
    public String kitapDetay(@PathVariable Long id, Model model) throws NoResourceFoundException {
        User user = CurrentUser.get();
        DonationView view = donationService.view(id, user)
                .orElseThrow(() -> new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/kitap/" + id));

        ClaimEligibility eligibility = donationService.eligibility(view, user);

        model.addAttribute("d", view);
        model.addAttribute("eligibility", eligibility);
        // İlk 7 kayıt yeterli: bakılan bağış aralarında olabilir, en fazla 6 tane gösterilir
        model.addAttribute("benzer", donationService.openDonations(DonationService.Filter.none(), PageRequest.of(0, 7))
                .stream()
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
            ra.addFlashAttribute("basari", features.isShipping()
                    ? "Kitap senin! Bağışçı kargoya verdiğinde haber vereceğiz."
                    : "Kitap senin! Bağışçıyla mesajlaşıp kampüste bir buluşma ayarlayabilirsin.");
            return "redirect:/aldiklarim";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/kitap/" + id;
        }
    }
}
