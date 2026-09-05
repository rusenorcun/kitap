package app.kitapla.web;

import app.kitapla.domain.Conversation;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.MessageService;
import app.kitapla.service.SseHub;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Eşleşme üzerinden mesajlaşma. */
@Controller
@RequestMapping("/mesajlar")
public class MessageController {

    private final MessageService messages;
    private final SseHub sse;

    public MessageController(MessageService messages, SseHub sse) {
        this.messages = messages;
        this.sse = sse;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User me = principal.getUser();
        var sohbetler = messages.mine(me);

        // Şablon her sohbet için okunmamış sayısını gösterir
        Map<Long, Long> okunmamis = new LinkedHashMap<>();
        sohbetler.forEach(c -> okunmamis.put(c.getId(), messages.unread(c, me)));

        model.addAttribute("sohbetler", sohbetler);
        model.addAttribute("okunmamis", okunmamis);
        return "mesajlar";
    }

    /** Alışveriş üzerinden sohbeti açar (yoksa oluşturur) ve içine yönlendirir. */
    @GetMapping("/ac/{kind}/{refId}")
    public String ac(@AuthenticationPrincipal AppUserDetails principal,
                     @PathVariable String kind, @PathVariable Long refId,
                     RedirectAttributes ra) {
        try {
            ConversationKind tur = ConversationKind.valueOf(kind.trim().toUpperCase(java.util.Locale.ROOT));
            Conversation c = messages.open(tur, refId, principal.getUser());
            return "redirect:/mesajlar/" + c.getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/mesajlar";
        }
    }

    @GetMapping("/{id}")
    public String sohbet(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id, Model model, RedirectAttributes ra) {
        User me = principal.getUser();
        try {
            Conversation c = messages.require(id, me);
            messages.markRead(c, me);
            model.addAttribute("sohbet", c);
            model.addAttribute("karsiTaraf", c.other(me));
            model.addAttribute("mesajlar", messages.messagesOf(c));
            model.addAttribute("ben", me);
            return "sohbet";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/mesajlar";
        }
    }

    /** HTMX: mesaj listesi parçası (canlı akış tetikleyince tazelenir). */
    @GetMapping("/{id}/liste")
    public String parca(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long id, Model model) {
        User me = principal.getUser();
        Conversation c = erisimDenetimi(id, me);
        messages.markRead(c, me);
        model.addAttribute("mesajlar", messages.messagesOf(c));
        model.addAttribute("ben", me);
        return "sohbet :: liste";
    }

    @PostMapping("/{id}")
    public String gonder(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         @RequestParam(required = false) String body,
                         RedirectAttributes ra) {
        try {
            messages.send(id, principal.getUser(), body);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/mesajlar/" + id;
    }

    /**
     * Canlı akış. Yeni mesaj olduğunda "yeni" olayı gönderir; içerik taşımaz,
     * tarayıcı listeyi tazeler. Böylece HTML kaçışıyla uğraşmak gerekmez.
     */
    @GetMapping(value = "/{id}/akis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter akis(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) {
        // Abone olmadan önce erişim denetimi: başkasının sohbetini dinleyemezsin
        erisimDenetimi(id, principal.getUser());
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return sse.subscribe(id);
    }

    /**
     * Yönlendirme yapılamayan uçlar (parça ve canlı akış) için erişim denetimi.
     * Başkasının sohbeti "sunucu hatası" değil, açıkça <b>403</b> ile reddedilir.
     */
    private Conversation erisimDenetimi(Long id, User me) {
        try {
            return messages.require(id, me);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }
}
