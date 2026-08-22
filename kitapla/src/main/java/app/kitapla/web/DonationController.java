package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookMetadata;
import app.kitapla.service.BookService;
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
    private final DonationService donationService;
    private final ClaimRepository claims;

    public DonationController(BookService bookService, DonationService donationService, ClaimRepository claims) {
        this.bookService = bookService;
        this.donationService = donationService;
        this.claims = claims;
    }

    @GetMapping("/bagis/yeni")
    public String yeniForm() {
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
                          @RequestParam(required = false) String description,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false) String targetLevel,
                          @RequestParam(required = false) String source,
                          RedirectAttributes ra, Model model) {
        User donor = principal.getUser();
        try {
            Book book = bookService.findOrCreate(title, author, purchaseLink, coverUrl, null, donor.getId());
            TargetLevel level = (targetLevel == null || targetLevel.isBlank())
                    ? TargetLevel.HEPSI : TargetLevel.valueOf(targetLevel);
            DonationSource src = (source == null || source.isBlank())
                    ? DonationSource.PURCHASE : DonationSource.valueOf(source);
            donationService.create(donor, book, quantity, level, src, description);
            ra.addFlashAttribute("basari", "Bağışın yayınlandı. İlk 48 saat öğrencilere öncelikli gösterilecek.");
            return "redirect:/bagislarim";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("hata", ex.getMessage());
            model.addAttribute("form", formEcho(title, author, purchaseLink, description, quantity, targetLevel, source));
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
