package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.AdminService;
import app.kitapla.domain.ReportKind;
import app.kitapla.service.MessageService;
import app.kitapla.service.PickupPointService;
import app.kitapla.service.ReportService;
import app.kitapla.domain.ConversationKind;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.SwapBookRepository;
import app.kitapla.repo.SwapOfferRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Yönetim paneli. Tüm uçlar SecurityConfig'te ROLE_ADMIN ile korunur.
 * Öğrenci belgeleri statik olarak servis edilmez; yalnızca buradan okunur.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final PickupPointService points;
    private final ReportService reports;
    private final MessageService messages;
    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;
    private final DonationRepository donations;
    private final SwapBookRepository swapBooks;

    public AdminController(AdminService admin, PickupPointService points,
                           ReportService reports, MessageService messages,
                           ClaimRepository claims, BookRequestRepository requests,
                           SwapOfferRepository offers, DonationRepository donations,
                           SwapBookRepository swapBooks) {
        this.admin = admin;
        this.points = points;
        this.reports = reports;
        this.messages = messages;
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
        this.donations = donations;
        this.swapBooks = swapBooks;
    }

    /** Alt navigasyondaki bekleyen belge rozeti her yönetim sayfasında görünür. */
    @ModelAttribute("bekleyenSayisi")
    public long bekleyenSayisi() {
        return admin.pendingDocumentCount();
    }

    /** Açık şikâyet rozeti. */
    @ModelAttribute("sikayetSayisi")
    public long sikayetSayisi() {
        return reports.openCount();
    }

    @GetMapping
    public String pano(Model model) {
        model.addAttribute("stats", admin.stats());
        model.addAttribute("bekleyenler", admin.pendingDocuments());
        return "admin";
    }

    // ---------- Öğrenci belgeleri ----------

    @GetMapping("/belgeler")
    public String belgeler(Model model) {
        model.addAttribute("bekleyenler", admin.pendingDocuments());
        return "admin-belgeler";
    }

    @PostMapping("/belgeler/{id}/onayla")
    public String onayla(@PathVariable Long id, RedirectAttributes ra) {
        try {
            User u = admin.approveStudent(id);
            ra.addFlashAttribute("basari", u.getName() + " artık onaylı öğrenci.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/belgeler";
    }

    @PostMapping("/belgeler/{id}/reddet")
    public String reddet(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        try {
            User u = admin.rejectStudent(id, reason);
            ra.addFlashAttribute("basari", u.getName() + " için belge reddedildi ve dosya silindi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/belgeler";
    }

    /** Belgeyi tarayıcıda gösterir (indirme değil); yalnızca yönetici erişebilir. */
    @GetMapping("/belge/{id}")
    public ResponseEntity<?> belge(@PathVariable Long id) {
        try {
            Path file = admin.documentPathOf(id);
            String type = app.kitapla.service.DocumentService.resolveContentType(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, type)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"belge-" + id + resolveExt(file) + "\"")
                    .contentLength(Files.size(file))
                    .body(new FileSystemResource(file));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(404).body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Belge okunamadı.");
        }
    }

    private static String resolveExt(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot);
    }

    // ---------- Üye yönetimi ----------

    @GetMapping("/uyeler")
    public String uyeler(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("uyeler", admin.searchUsers(q));
        model.addAttribute("q", q);
        return "admin-uyeler";
    }

    @PostMapping("/uyeler/{id}/askiya-al")
    public String askiyaAl(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id, RedirectAttributes ra) {
        return uyeIslemi(ra, () -> admin.setBlocked(principal.getUser(), id, true), "askıya alındı");
    }

    @PostMapping("/uyeler/{id}/aktif-et")
    public String aktifEt(@AuthenticationPrincipal AppUserDetails principal,
                          @PathVariable Long id, RedirectAttributes ra) {
        return uyeIslemi(ra, () -> admin.setBlocked(principal.getUser(), id, false), "yeniden aktif");
    }

    @PostMapping("/uyeler/{id}/yetki-ver")
    public String yetkiVer(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id, RedirectAttributes ra) {
        return uyeIslemi(ra, () -> admin.setAdmin(principal.getUser(), id, true), "artık yönetici");
    }

    @PostMapping("/uyeler/{id}/yetki-al")
    public String yetkiAl(@AuthenticationPrincipal AppUserDetails principal,
                          @PathVariable Long id, RedirectAttributes ra) {
        return uyeIslemi(ra, () -> admin.setAdmin(principal.getUser(), id, false), "artık yönetici değil");
    }

    @PostMapping("/uyeler/{id}/sil")
    public String sil(@AuthenticationPrincipal AppUserDetails principal,
                      @PathVariable Long id, RedirectAttributes ra) {
        try {
            admin.deleteUser(principal.getUser(), id);
            ra.addFlashAttribute("basari", "Üye silindi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/uyeler";
    }

    private String uyeIslemi(RedirectAttributes ra, java.util.function.Supplier<User> action, String sonuc) {
        try {
            User u = action.get();
            ra.addFlashAttribute("basari", u.getName() + " " + sonuc + ".");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/uyeler";
    }

    // ---------- Teslim noktaları ----------

    @GetMapping("/noktalar")
    public String noktalar(Model model) {
        model.addAttribute("noktalar", points.all());
        return "admin-noktalar";
    }

    @PostMapping("/noktalar")
    public String noktaEkle(@RequestParam(required = false) String campus,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String description,
                            RedirectAttributes ra) {
        return noktaIslemi(ra, () -> {
            var p = points.create(campus, name, description);
            return p.getFullName() + " eklendi.";
        });
    }

    @PostMapping("/noktalar/{id}/guncelle")
    public String noktaGuncelle(@PathVariable Long id,
                                @RequestParam(required = false) String campus,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) String description,
                                RedirectAttributes ra) {
        return noktaIslemi(ra, () -> {
            var p = points.update(id, campus, name, description);
            return p.getFullName() + " güncellendi.";
        });
    }

    @PostMapping("/noktalar/{id}/pasiflestir")
    public String noktaPasif(@PathVariable Long id, RedirectAttributes ra) {
        return noktaIslemi(ra, () -> points.setActive(id, false).getFullName() + " pasifleştirildi.");
    }

    @PostMapping("/noktalar/{id}/aktiflestir")
    public String noktaAktif(@PathVariable Long id, RedirectAttributes ra) {
        return noktaIslemi(ra, () -> points.setActive(id, true).getFullName() + " yeniden aktif.");
    }

    private String noktaIslemi(RedirectAttributes ra, java.util.function.Supplier<String> action) {
        try {
            ra.addFlashAttribute("basari", action.get());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/noktalar";
    }

    // ---------- Şikâyetler ----------

    @GetMapping("/sikayetler")
    public String sikayetler(@RequestParam(required = false) String tumu, Model model) {
        boolean hepsi = tumu != null;
        model.addAttribute("sikayetler", hepsi ? reports.all() : reports.open());
        model.addAttribute("hepsi", hepsi);
        return "admin-sikayetler";
    }

    /**
     * Şikâyet edilen içeriği gösterir. Sohbetlerde mesajlar yalnızca açık
     * şikâyet varsa okunabilir; bu kural servis katmanında zorlanır.
     */
    @GetMapping("/sikayetler/{id}")
    public String sikayet(@PathVariable Long id, Model model, RedirectAttributes ra) {
        var r = reports.find(id).orElse(null);
        if (r == null) {
            ra.addFlashAttribute("hata", "Şikâyet bulunamadı.");
            return "redirect:/admin/sikayetler";
        }
        model.addAttribute("sikayet", r);

        if (r.getKind() == ReportKind.CONVERSATION) {
            try {
                var sohbet = messages.requireForModeration(r.getRefId(), reports);
                model.addAttribute("sohbet", sohbet);
                model.addAttribute("mesajlar", messages.messagesOf(sohbet));
            } catch (IllegalStateException ex) {
                model.addAttribute("sohbetHatasi", ex.getMessage());
            }
        } else if (r.getKind() == ReportKind.CLAIM) {
            claims.findByIdWithDetails(r.getRefId()).ifPresent(c -> model.addAttribute("claim", c));
        } else if (r.getKind() == ReportKind.REQUEST) {
            requests.findByIdWithDetails(r.getRefId()).ifPresent(req -> model.addAttribute("request", req));
        } else if (r.getKind() == ReportKind.SWAP_OFFER) {
            offers.findByIdWithDetails(r.getRefId()).ifPresent(o -> model.addAttribute("offer", o));
        } else if (r.getKind() == ReportKind.DONATION) {
            donations.findByIdWithDetails(r.getRefId()).ifPresent(d -> model.addAttribute("donation", d));
        } else if (r.getKind() == ReportKind.SWAP_BOOK) {
            swapBooks.findByIdWithDetails(r.getRefId()).ifPresent(sb -> model.addAttribute("swapBook", sb));
        }

        // Şikâyet destek sohbeti (yönetici ile üye arasındaki irtibat)
        messages.find(ConversationKind.REPORT, r.getId()).ifPresent(destekSohbeti -> {
            model.addAttribute("destekSohbeti", destekSohbeti);
            model.addAttribute("destekMesajlari", messages.messagesOf(destekSohbeti));
        });

        return "admin-sikayet";
    }

    @PostMapping("/sikayetler/{id}/mesaj")
    public String sikayetMesajGonder(@AuthenticationPrincipal AppUserDetails principal,
                                     @PathVariable Long id,
                                     @RequestParam(required = false) String body,
                                     RedirectAttributes ra) {
        try {
            var c = messages.open(ConversationKind.REPORT, id, principal.getUser());
            messages.send(c.getId(), principal.getUser(), body);
            ra.addFlashAttribute("basari", "Mesajınız kullanıcıya iletildi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/sikayetler/" + id;
    }

    @PostMapping("/sikayetler/{id}/sonuclandir")
    public String sikayetSonuclandir(@AuthenticationPrincipal AppUserDetails principal,
                                     @PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean actioned,
                                     @RequestParam(required = false) String adminNote,
                                     RedirectAttributes ra) {
        try {
            reports.resolve(id, principal.getUser(), actioned, adminNote);
            ra.addFlashAttribute("basari", actioned
                    ? "Şikâyet işleme alındı olarak kapatıldı."
                    : "Şikâyet, işlem gerekmedi olarak kapatıldı.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/sikayetler";
    }

    // ---------- İçerik moderasyonu ----------

    @GetMapping("/icerik")
    public String icerik(Model model) {
        model.addAttribute("bagislar", admin.openDonations());
        model.addAttribute("istekler", admin.openRequests());
        model.addAttribute("takaslar", admin.openSwapBooks());
        return "admin-icerik";
    }

    @PostMapping("/icerik/bagis/{id}/kaldir")
    public String bagisKaldir(@PathVariable Long id, @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        return icerikIslemi(ra, () -> admin.removeDonation(id, reason), "Bağış yayından kaldırıldı.");
    }

    @PostMapping("/icerik/istek/{id}/kaldir")
    public String istekKaldir(@PathVariable Long id, @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        return icerikIslemi(ra, () -> admin.removeRequest(id, reason), "İstek kaldırıldı.");
    }

    @PostMapping("/icerik/takas/{id}/kaldir")
    public String takasKaldir(@PathVariable Long id, @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        return icerikIslemi(ra, () -> admin.removeSwapBook(id, reason), "Takas ilanı kaldırıldı.");
    }

    private String icerikIslemi(RedirectAttributes ra, Runnable action, String okMessage) {
        try {
            action.run();
            ra.addFlashAttribute("basari", okMessage);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/admin/icerik";
    }
}
