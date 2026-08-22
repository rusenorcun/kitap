package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bildirimler: liste ve okundu işaretleme (HTMX ile satır bazlı). */
@Controller
@RequestMapping("/bildirimler")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("notifications", notifications.latest(user));
        return "bildirimler";
    }

    /** HTMX: tek bildirimi okundu işaretle, güncellenmiş satırı döndür. */
    @PostMapping("/{id}/okundu")
    public String okundu(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id, Model model) {
        User user = principal.getUser();
        notifications.markRead(user, id);
        model.addAttribute("notifications", notifications.latest(user));
        return "bildirimler :: liste";
    }

    @PostMapping("/hepsi-okundu")
    public String hepsiOkundu(@AuthenticationPrincipal AppUserDetails principal, RedirectAttributes ra) {
        int n = notifications.markAllRead(principal.getUser());
        ra.addFlashAttribute("basari", n == 0 ? "Okunmamış bildirim yoktu." : n + " bildirim okundu işaretlendi.");
        return "redirect:/bildirimler";
    }
}
