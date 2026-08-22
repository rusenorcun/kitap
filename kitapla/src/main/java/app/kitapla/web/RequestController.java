package app.kitapla.web;

import app.kitapla.domain.Book;
import app.kitapla.domain.DonationSource;
import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.BookService;
import app.kitapla.service.RequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Kitap istekleri: oluşturma, açık istekler, karşılama ve teslimat. */
@Controller
public class RequestController {

    private final RequestService requestService;
    private final BookService bookService;

    public RequestController(RequestService requestService, BookService bookService) {
        this.requestService = requestService;
        this.bookService = bookService;
    }

    /** Açık istekler — herkese açık; teslimat adresi burada GÖSTERİLMEZ. */
    @GetMapping("/istekler")
    public String istekler(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("requests", requestService.openRequests(q));
        model.addAttribute("q", q);
        model.addAttribute("me", CurrentUser.get());
        return "istekler";
    }

    @GetMapping("/istek/yeni")
    public String yeniForm() {
        return "istek-yeni";
    }

    @PostMapping("/istek/yeni")
    public String olustur(@AuthenticationPrincipal AppUserDetails principal,
                          @RequestParam(required = false) String title,
                          @RequestParam(required = false) String author,
                          @RequestParam(required = false) String purchaseLink,
                          @RequestParam(required = false) String description,
                          RedirectAttributes ra, Model model) {
        User user = principal.getUser();
        try {
            Book book = bookService.findOrCreate(title, author, purchaseLink, null, null, user.getId());
            requestService.create(user, book, description);
            ra.addFlashAttribute("basari", "İsteğin yayınlandı. Bir bağışçı karşıladığında haber vereceğiz.");
            return "redirect:/isteklerim";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("hata", ex.getMessage());
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("title", title);
            form.put("author", author);
            form.put("purchaseLink", purchaseLink);
            form.put("description", description);
            model.addAttribute("form", form);
            return "istek-yeni";
        }
    }

    @GetMapping("/isteklerim")
    public String isteklerim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("requests", requestService.myRequests(principal.getUser()));
        return "isteklerim";
    }

    /** Karşıladığım istekler — teslimat adresi burada gösterilir. */
    @GetMapping("/karsiladiklarim")
    public String karsiladiklarim(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("requests", requestService.fulfilledByMe(principal.getUser()));
        return "karsiladiklarim";
    }

    @PostMapping("/istek/{id}/karsila")
    public String karsila(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                          @RequestParam(required = false) String source, RedirectAttributes ra) {
        try {
            DonationSource src = (source == null || source.isBlank())
                    ? DonationSource.PURCHASE : DonationSource.valueOf(source);
            requestService.fulfill(id, principal.getUser(), src);
            ra.addFlashAttribute("basari", "İsteği karşıladın. Teslimat adresi aşağıda; kargoladığında işaretle.");
            return "redirect:/karsiladiklarim";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/istekler";
        }
    }

    @PostMapping("/istek/{id}/kargola")
    public String kargola(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> requestService.ship(id, principal.getUser()),
                "Kargo bilgisi kaydedildi. Alıcıya haber verildi.", "/karsiladiklarim");
    }

    @PostMapping("/istek/{id}/teslim-aldim")
    public String teslimAldim(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> requestService.deliver(id, principal.getUser()),
                "Teslim aldığın kaydedildi. Karşılayana teşekkür edebilirsin.", "/isteklerim");
    }

    @PostMapping("/istek/{id}/tesekkur")
    public String tesekkur(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                           @RequestParam(required = false) String message, RedirectAttributes ra) {
        return run(ra, () -> requestService.thank(id, principal.getUser(), message),
                "Teşekkürün iletildi.", "/isteklerim");
    }

    @PostMapping("/istek/{id}/sil")
    public String sil(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, RedirectAttributes ra) {
        return run(ra, () -> requestService.delete(id, principal.getUser()),
                "İsteğin kaldırıldı.", "/isteklerim");
    }

    private String run(RedirectAttributes ra, Runnable action, String okMessage, String target) {
        try {
            action.run();
            ra.addFlashAttribute("basari", okMessage);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + target;
    }
}
