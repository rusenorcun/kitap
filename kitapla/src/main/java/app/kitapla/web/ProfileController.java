package app.kitapla.web;

import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
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
    private final UserRepository users;
    private final Path documentDir;

    public ProfileController(UserService userService, QuotaService quotaService, UserRepository users,
                             @Value("${kitapla.upload-dir}") String uploadDir) {
        this.userService = userService;
        this.quotaService = quotaService;
        this.users = users;
        this.documentDir = Path.of(uploadDir, "documents");
    }

    /** Oturumdaki kullanıcı nesnesi güncellemelerden sonra bayat kalabilir; veritabanından tazeler. */
    private User fresh(AppUserDetails principal) {
        return users.findById(principal.getUser().getId()).orElse(principal.getUser());
    }

    @GetMapping
    public String profil(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = fresh(principal);
        model.addAttribute("user", user);
        model.addAttribute("quota", quotaService.quotaFor(user));
        return "profil";
    }

    @PostMapping
    public String guncelle(@AuthenticationPrincipal AppUserDetails principal,
                           @RequestParam String name,
                           @RequestParam(required = false) String address,
                           @RequestParam(required = false) String phone,
                           RedirectAttributes ra) {
        try {
            userService.updateProfile(principal.getUser(), name, address, phone);
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
        model.addAttribute("user", fresh(principal));
        return "profil-ogrenci";
    }

    @PostMapping("/ogrenci")
    public String ogrenciBasvuru(@AuthenticationPrincipal AppUserDetails principal,
                                 @RequestParam(required = false) String schoolLevel,
                                 @RequestParam(required = false) String documentNo,
                                 @RequestParam(required = false) MultipartFile document,
                                 RedirectAttributes ra) {
        String savedPath = null;
        try {
            if (document != null && !document.isEmpty()) {
                Files.createDirectories(documentDir);
                String safe = document.getOriginalFilename() == null ? "belge"
                        : document.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
                String fileName = System.currentTimeMillis() + "-" + safe;
                document.transferTo(documentDir.resolve(fileName));
                savedPath = fileName;
            }
            SchoolLevel level = (schoolLevel == null || schoolLevel.isBlank())
                    ? null : SchoolLevel.valueOf(schoolLevel);
            userService.applyForStudent(principal.getUser(), level, documentNo, savedPath);
            ra.addFlashAttribute("basari",
                    "Belgen incelemeye alındı. Onaylandığında bağışlarda 48 saat öncelik kazanacaksın.");
            return "redirect:/profil";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            discard(savedPath);
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/profil/ogrenci";
        } catch (Exception ex) {
            discard(savedPath);
            ra.addFlashAttribute("hata", "Belge yüklenirken bir sorun oldu: " + ex.getMessage());
            return "redirect:/profil/ogrenci";
        }
    }

    /** Başvuru kaydedilemediyse diske yazılan belgeyi geride bırakma. */
    private void discard(String fileName) {
        if (fileName == null) return;
        try {
            Files.deleteIfExists(documentDir.resolve(fileName));
        } catch (Exception ignored) {
            // temizlik başarısız olsa da kullanıcıya dönen hata mesajı önemli
        }
    }
}
