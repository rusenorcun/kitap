package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.AdminService;
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

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    /** Alt navigasyondaki bekleyen belge rozeti her yönetim sayfasında görünür. */
    @ModelAttribute("bekleyenSayisi")
    public long bekleyenSayisi() {
        return admin.pendingDocumentCount();
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
            String type = URLConnection.guessContentTypeFromName(file.getFileName().toString());
            if (type == null) type = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, type)
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
