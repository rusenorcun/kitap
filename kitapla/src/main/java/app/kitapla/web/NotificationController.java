package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.NotificationService;
import app.kitapla.service.NotificationSseHub;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bildirimler: liste, okundu işaretleme ve canlı SSE akışı. */
@Controller
@RequestMapping("/bildirimler")
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationSseHub sseHub;

    public NotificationController(NotificationService notifications, NotificationSseHub sseHub) {
        this.notifications = notifications;
        this.sseHub = sseHub;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("notifications", notifications.latest(user));
        return "bildirimler";
    }

    /** Canlı bildirim akışı: yeni bildirim olduğunda okunmamış sayısını iletir. */
    @GetMapping(value = "/akis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter akis(@AuthenticationPrincipal AppUserDetails principal, HttpServletResponse response) {
        User user = principal.getUser();
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return sseHub.subscribe(user.getId(), notifications.unreadCount(user));
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

