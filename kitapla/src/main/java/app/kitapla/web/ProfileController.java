package app.kitapla.web;

import app.kitapla.config.Features;
import app.kitapla.domain.School;
import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.QuotaService;
import app.kitapla.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;

/** Profil, şifre ve öğrenci doğrulama başvurusu. */
@Controller
@RequestMapping("/profil")
public class ProfileController {

    private final UserService userService;
    private final QuotaService quotaService;
    private final Features features;
    private final app.kitapla.service.DocumentService documentService;

    public ProfileController(UserService userService, QuotaService quotaService, Features features,
                             app.kitapla.service.DocumentService documentService) {
        this.userService = userService;
        this.quotaService = quotaService;
        this.features = features;
        this.documentService = documentService;
    }

    @GetMapping
    public String profil(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("user", user);
        model.addAttribute("quota", quotaService.quotaFor(user));
        return "profil";
    }

    @PostMapping
    public String guncelle(@AuthenticationPrincipal AppUserDetails principal,
                           @RequestParam String name,
                           @RequestParam(required = false) String address,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String school,
                           RedirectAttributes ra) {
        try {
            // Adres alanı kampüs modunda formda hiç yok; gelmeyen değer kaydı silmesin.
            String adres = features.isAddress() ? address : principal.getUser().getAddress();
            userService.updateProfile(principal.getUser(), name, adres, phone, School.of(school));
            ra.addFlashAttribute("basari", "Profilin güncellendi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/profil";
    }

    @PostMapping("/sifre")
    public String sifre(@AuthenticationPrincipal AppUserDetails principal,
                        @RequestParam(required = false) String currentPassword,
                        @RequestParam(required = false) String newPassword,
                        @RequestParam(required = false) String confirmPassword,
                        RedirectAttributes ra) {
        try {
            userService.changePassword(principal.getUser(), currentPassword, newPassword, confirmPassword);
            ra.addFlashAttribute("basari", "Şifren güncellendi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/profil";
    }

    @GetMapping("/ogrenci")
    public String ogrenciForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("user", principal.getUser());
        return "profil-ogrenci";
    }

    /** Okul e-postasıyla öğrenci doğrulama; belgesiz ve anında. */
    @PostMapping("/ogrenci/eposta")
    public String okulEpostasi(@AuthenticationPrincipal AppUserDetails principal,
                               @RequestParam(required = false) String studentEmail,
                               RedirectAttributes ra) {
        try {
            userService.verifyStudentEmail(principal.getUser(), studentEmail);
            ra.addFlashAttribute("basari",
                    "Okul adresin kaydedildi, artık öğrencisin. Yeni bağışlarda ilk 48 saat önceliklisin.");
            return "redirect:/profil";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/profil/ogrenci";
        }
    }

    @PostMapping("/ogrenci")
    public String ogrenciBasvuru(@AuthenticationPrincipal AppUserDetails principal,
                                 @RequestParam(required = false) String schoolLevel,
                                 @RequestParam(required = false) String documentNo,
                                 @RequestParam(required = false) MultipartFile document,
                                 RedirectAttributes ra) {
        if (!features.isDocument()) {
            ra.addFlashAttribute("hata", "Öğrenci doğrulaması okul e-postasıyla yapılıyor.");
            return "redirect:/profil/ogrenci";
        }

        String savedPath = null;
        try {
            if (document != null && !document.isEmpty()) {
                savedPath = documentService.save(document);
            }
            SchoolLevel level = (schoolLevel == null || schoolLevel.isBlank())
                    ? null : SchoolLevel.valueOf(schoolLevel.trim().toUpperCase(java.util.Locale.ROOT));
            userService.applyForStudent(principal.getUser(), level, documentNo, savedPath);
            ra.addFlashAttribute("basari",
                    "Belgen incelemeye alındı. Onaylandığında bağışlarda 48 saat öncelik kazanacaksın.");
            return "redirect:/profil";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            documentService.discard(savedPath);
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/profil/ogrenci";
        } catch (Exception ex) {
            documentService.discard(savedPath);
            ra.addFlashAttribute("hata", "Belge yüklenirken bir sorun oldu: " + ex.getMessage());
            return "redirect:/profil/ogrenci";
        }
    }
}
