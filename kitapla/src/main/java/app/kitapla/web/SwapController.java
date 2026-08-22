package app.kitapla.web;

import app.kitapla.domain.Book;
import app.kitapla.domain.SwapBookStatus;
import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookService;
import app.kitapla.service.SwapService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Kitap takası: keşif, kendi kitapların, teklifler ve karşılıklı kargo. */
@Controller
@RequestMapping("/takas")
public class SwapController {

    private final SwapService swapService;
    private final BookService bookService;

    public SwapController(SwapService swapService, BookService bookService) {
        this.swapService = swapService;
        this.bookService = bookService;
    }

    /** Takasa açık kitaplar + gelen teklifler. */
    @GetMapping
    public String takas(@AuthenticationPrincipal AppUserDetails principal,
                        @RequestParam(required = false) String q, Model model) {
        User me = principal.getUser();
        model.addAttribute("books", swapService.discover(me, q));
        model.addAttribute("incoming", swapService.incoming(me).stream()
                .filter(o -> o.getStatus().name().equals("PENDING")).toList());
        model.addAttribute("myOpenBooks", swapService.myOpenBooks(me));
        model.addAttribute("q", q);
        model.addAttribute("svc", swapService);
        model.addAttribute("me", me);
        return "takas";
    }

    /** Kendi takas kitaplarım. */
    @GetMapping("/kitaplarim")
    public String kitaplarim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("books", swapService.myBooks(principal.getUser()));
        return "takas-kitaplarim";
    }

    @PostMapping("/kitaplarim")
    public String kitapEkle(@AuthenticationPrincipal AppUserDetails principal,
                            @RequestParam(required = false) String title,
                            @RequestParam(required = false) String author,
                            @RequestParam(required = false) String note,
                            RedirectAttributes ra) {
        User me = principal.getUser();
        try {
            Book book = bookService.findOrCreate(title, author, null, null, null, me.getId());
            swapService.open(me, book, note);
            ra.addFlashAttribute("basari", "Kitabın takasa açıldı.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/takas/kitaplarim";
    }

    @PostMapping("/kitaplarim/{id}/durum")
    public String durum(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                        @RequestParam String status, RedirectAttributes ra) {
        return run(ra, () -> swapService.setStatus(id, principal.getUser(), SwapBookStatus.valueOf(status)),
                "OPEN".equals(status) ? "Kitap takasa açıldı." : "Kitap takastan gizlendi.", "/takas/kitaplarim");
    }

    @PostMapping("/kitaplarim/{id}/sil")
    public String kitapSil(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> swapService.removeBook(id, principal.getUser()),
                "Kitap takastan kaldırıldı.", "/takas/kitaplarim");
    }

    /** Teklif formu: hedef kitaba karşılık kendi kitaplarımdan biri. */
    @GetMapping("/teklif/{targetId}")
    public String teklifForm(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long targetId, Model model, RedirectAttributes ra) {
        User me = principal.getUser();
        var target = swapService.viewBook(targetId);
        if (target.isEmpty()) {
            ra.addFlashAttribute("hata", "Kitap bulunamadı.");
            return "redirect:/takas";
        }
        model.addAttribute("target", target.get());
        model.addAttribute("myBooks", swapService.myOpenBooks(me));
        return "takas-teklif";
    }

    @PostMapping("/teklif/{targetId}")
    public String teklifVer(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long targetId,
                            @RequestParam Long offeredId, @RequestParam(required = false) String message,
                            RedirectAttributes ra) {
        try {
            swapService.offer(targetId, offeredId, principal.getUser(), message);
            ra.addFlashAttribute("basari", "Teklifin gönderildi. Karşı taraf kabul ederse adresler paylaşılacak.");
            return "redirect:/takas/takaslarim";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/takas/teklif/" + targetId;
        }
    }

    /** Gelen ve giden tekliflerim. */
    @GetMapping("/takaslarim")
    public String takaslarim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User me = principal.getUser();
        model.addAttribute("incoming", swapService.incoming(me));
        model.addAttribute("outgoing", swapService.outgoing(me));
        model.addAttribute("svc", swapService);
        model.addAttribute("me", me);
        return "takaslarim";
    }

    @PostMapping("/teklif/{id}/kabul")
    public String kabul(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> swapService.accept(id, principal.getUser()),
                "Takası kabul ettin. Adresler paylaşıldı; kitabı kargolayabilirsin.", "/takas/takaslarim");
    }

    @PostMapping("/teklif/{id}/reddet")
    public String reddet(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> swapService.reject(id, principal.getUser()), "Teklif reddedildi.", "/takas/takaslarim");
    }

    @PostMapping("/teklif/{id}/geri-cek")
    public String geriCek(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> swapService.cancel(id, principal.getUser()), "Teklifin geri çekildi.", "/takas/takaslarim");
    }

    @PostMapping("/teklif/{id}/kargola")
    public String kargola(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> swapService.ship(id, principal.getUser()),
                "Kargo bilgin kaydedildi. İki taraf da kargoladığında takas tamamlanır.", "/takas/takaslarim");
    }

    private String run(RedirectAttributes ra, Runnable action, String okMessage, String target) {
        try {
            action.run();
            ra.addFlashAttribute("basari", okMessage);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + target;
    }
}
