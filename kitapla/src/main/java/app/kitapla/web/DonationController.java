package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookMetadata;
import app.kitapla.service.BookService;
import app.kitapla.service.PickupPointService;
import app.kitapla.service.DonationService;
import app.kitapla.service.DonationView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bağış oluşturma, bağışlarım ve teslimat (kargola) işlemleri. */
@Controller
public class DonationController {

    private final BookService bookService;
    private final app.kitapla.service.CoverService coverService;
    private final DonationService donationService;
    private final ClaimRepository claims;
    private final PickupPointService points;
    private final app.kitapla.config.Features features;

    public DonationController(BookService bookService, app.kitapla.service.CoverService coverService,
                              DonationService donationService,
                              ClaimRepository claims, PickupPointService points,
                              app.kitapla.config.Features features) {
        this.bookService = bookService;
        this.coverService = coverService;
        this.donationService = donationService;
        this.claims = claims;
        this.points = points;
        this.features = features;
    }

    @GetMapping("/bagis/yeni")
    public String yeniForm(Model model) {
        model.addAttribute("noktalar", points.active());
        return "bagis-yeni";
    }

    /** HTMX: linkten başlık/kapak önizlemesi (kaydetmez). */
    @PostMapping("/bagis/onizleme")
    public String onizleme(@RequestParam(required = false) String purchaseLink, Model model) {
        BookMetadata meta = bookService.preview(purchaseLink);
        model.addAttribute("meta", meta);
        model.addAttribute("bulunamadi", meta.isEmpty());
        return "bagis-yeni :: onizleme";
    }

    @PostMapping("/bagis/yeni")
    public String olustur(@AuthenticationPrincipal AppUserDetails principal,
                          @RequestParam(required = false) String title,
                          @RequestParam(required = false) String author,
                          @RequestParam(required = false) String purchaseLink,
                          @RequestParam(required = false) String coverUrl,
                          @RequestParam(required = false) org.springframework.web.multipart.MultipartFile coverFile,
                          @RequestParam(required = false) String description,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false) String targetLevel,
                          @RequestParam(required = false) String source,
                          @RequestParam(required = false) Long pointId,
                          @RequestParam(required = false) String pointNote,
                          RedirectAttributes ra, Model model) {
        User donor = principal.getUser();
        try {
            // Elle girişte yüklenen görsel, linkten gelen kapağın önüne geçer.
            String kapak = coverService.resolve(coverFile, coverUrl);
            Book book = bookService.findOrCreate(title, author, purchaseLink, kapak, null, donor.getId());
            TargetLevel level = (targetLevel == null || targetLevel.isBlank())
                    ? TargetLevel.HEPSI : TargetLevel.valueOf(targetLevel.trim().toUpperCase(java.util.Locale.ROOT));
            // Satın alma kapalıyken kaynak sorulmaz; elindeki kopya varsayılır
            DonationSource src = (source == null || source.isBlank())
                    ? (features.isPurchase() ? DonationSource.PURCHASE : DonationSource.OWN)
                    : DonationSource.valueOf(source.trim().toUpperCase(java.util.Locale.ROOT));
            donationService.create(donor, book, quantity, level, src, description, pointId, pointNote);
            ra.addFlashAttribute("basari", "Bağışın yayınlandı. İlk 48 saat öğrencilere öncelikli gösterilecek.");
            return "redirect:/bagislarim";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("hata", ex.getMessage());
            model.addAttribute("form", formEcho(title, author, purchaseLink, description, quantity, targetLevel, source));
            model.addAttribute("noktalar", points.active());
            return "bagis-yeni";
        }
    }

    private Map<String, Object> formEcho(String title, String author, String link, String description,
                                         int quantity, String level, String source) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("title", title);
        f.put("author", author);
        f.put("purchaseLink", link);
        f.put("description", description);
        f.put("quantity", quantity);
        f.put("targetLevel", level);
        f.put("source", source);
        return f;
    }

    @GetMapping("/bagislarim")
    public String bagislarim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User donor = principal.getUser();
        List<DonationView> list = donationService.myDonations(donor);
        // Her bağış için alanlar (adres yalnızca bağışçıya gösterilir)
        Map<Long, List<Claim>> claimers = new LinkedHashMap<>();
        for (DonationView v : list) {
            claimers.put(v.getId(), claims.findByDonationWithStudent(v.donation()));
        }
        model.addAttribute("donations", list);
        model.addAttribute("claimers", claimers);
        model.addAttribute("noktalar", points.active());
        return "bagislarim";
    }

    @PostMapping("/bagis/{id}/kapat")
    public String kapat(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> donationService.close(id, principal.getUser()), "Bağış kapatıldı.");
    }

    @PostMapping("/bagis/{id}/ac")
    public String ac(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> donationService.reopen(id, principal.getUser()), "Bağış yeniden açıldı.");
    }

    @PostMapping("/bagis/{id}/sil")
    public String sil(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> donationService.delete(id, principal.getUser()), "Bağış silindi.");
    }

    @PostMapping("/bagis/{id}/takasa-aktar")
    public String takasaAktar(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                              @RequestParam(required = false) String note, RedirectAttributes ra) {
        return run(ra, () -> donationService.moveToSwap(id, principal.getUser(), note),
                "Kitap bağıştan kaldırıldı ve takasa açıldı.");
    }

    @PostMapping("/teslimat/{claimId}/kargola")
    public String kargola(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long claimId, RedirectAttributes ra) {
        return run(ra, () -> donationService.ship(claimId, principal.getUser()),
                "Kargo bilgisi kaydedildi. Alıcıya haber verildi.");
    }

    private String run(RedirectAttributes ra, Runnable action, String okMessage) {
        try {
            action.run();
            ra.addFlashAttribute("basari", okMessage);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/bagislarim";
    }
}
