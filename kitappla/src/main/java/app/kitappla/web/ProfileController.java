package app.kitappla.web;

import app.kitappla.config.Features;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import app.kitappla.domain.School;
import app.kitappla.domain.SchoolLevel;
import app.kitappla.domain.User;
import app.kitappla.security.AppUserDetails;
import app.kitappla.service.QuotaService;
import app.kitappla.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Profil, şifre ve öğrenci doğrulama başvurusu. */
@Controller
@RequestMapping("/profil")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UserService userService;
    private final QuotaService quotaService;
    private final Features features;
    private final app.kitappla.service.DocumentService documentService;

    public ProfileController(UserService userService, QuotaService quotaService, Features features,
                             app.kitappla.service.DocumentService documentService) {
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
            // Adres alanı yalnızca kargo/adres modunda formda var; null giderse kayıttaki adres korunur.
            String adres = features.isAddress() ? address : null;
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
                        jakarta.servlet.http.HttpServletRequest request,
                        RedirectAttributes ra) {
        try {
            userService.changePassword(principal.getUser(), currentPassword, newPassword, confirmPassword);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/profil";
        }
        // Şifre değişince bu dahil tüm oturumlar düşürülür. Oturum burada açıkça kapatılmazsa
        // sonraki istekte "oturum doldu" yönlendirmesi olur ve başarı mesajı kaybolurdu.
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login?sifre-degisti";
    }

    @GetMapping("/ogrenci")
    public String ogrenciForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("user", principal.getUser());
        return "profil-ogrenci";
    }

    @PostMapping("/ogrenci/eposta")
    public String okulEpostasi(@AuthenticationPrincipal AppUserDetails principal,
                               @RequestParam(required = false) String studentEmail,
                               RedirectAttributes ra) {
        try {
            User updated = userService.verifyStudentEmail(principal.getUser(), studentEmail);
            ra.addFlashAttribute(updated.isStudentVerificationSent() ? "basari" : "hata",
                    userService.studentVerificationMessage(updated));
            return "redirect:/profil";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/profil/ogrenci";
        }
    }

    @GetMapping("/ogrenci/eposta/onay")
    public String epostaOnayForm(@AuthenticationPrincipal AppUserDetails principal,
                                 @RequestParam(required = false) String token, Model model,
                                 jakarta.servlet.http.HttpServletResponse response,
                                 RedirectAttributes ra) {
        if (token == null || token.isBlank()) {
            ra.addFlashAttribute("hata", "Geçersiz veya eksik doğrulama bağlantısı.");
            return "redirect:/profil/ogrenci";
        }
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("user", principal.getUser());
        model.addAttribute("token", token);
        return "ogrenci-eposta-onay";
    }

    @PostMapping("/ogrenci/eposta/onay")
    public String epostaOnay(@AuthenticationPrincipal AppUserDetails principal,
                             @RequestParam(required = false) String token, RedirectAttributes ra) {
        if (token == null || token.isBlank()) {
            ra.addFlashAttribute("hata", "Doğrulama jetonu eksik.");
            return "redirect:/profil/ogrenci";
        }
        try {
            userService.confirmStudentEmail(principal.getUser(), token);
            ra.addFlashAttribute("basari", "Okul e-postan doğrulandı. Öğrenci önceliğin etkinleştirildi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/profil";
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
            // Beklenmeyen hatanın iç metni (dosya yolu, sürücü hatası) kullanıcıya gösterilmez.
            log.error("Öğrenci belgesi yüklenirken beklenmeyen hata", ex);
            documentService.discard(savedPath);
            ra.addFlashAttribute("hata", "Belge yüklenirken bir sorun oldu. Lütfen tekrar dene.");
            return "redirect:/profil/ogrenci";
        }
    }
}
