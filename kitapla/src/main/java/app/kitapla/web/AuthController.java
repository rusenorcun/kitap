package app.kitapla.web;

import app.kitapla.config.Features;
import app.kitapla.domain.School;
import app.kitapla.domain.SchoolLevel;
import app.kitapla.security.LoginAttemptService;
import app.kitapla.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private final UserService userService;
    private final LoginAttemptService attempts;
    private final Features features;
    private final Path uploadDir;

    public AuthController(UserService userService, LoginAttemptService attempts, Features features,
                          @Value("${kitapla.upload-dir}") String uploadDir) {
        this.userService = userService;
        this.attempts = attempts;
        this.features = features;
        this.uploadDir = Path.of(uploadDir, "documents");
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("kilitDakika", attempts.windowMinutes());
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String address,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String school,
                           @RequestParam(required = false, defaultValue = "false") boolean wantsStudent,
                           @RequestParam(required = false) String schoolLevel,
                           @RequestParam(required = false) String documentNo,
                           @RequestParam(required = false) MultipartFile document,
                           Model model) {
        // Belgeli başvuru kapalıyken kayıt formunda bu alanlar hiç gösterilmez;
        // gönderilseler bile dikkate alınmazlar.
        if (!features.isDocument()) wantsStudent = false;

        String documentPath = null;
        try {
            if (wantsStudent && document != null && !document.isEmpty()) {
                Files.createDirectories(uploadDir);
                String safe = document.getOriginalFilename() == null ? "belge"
                        : document.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
                String fname = System.currentTimeMillis() + "-" + safe;
                document.transferTo(uploadDir.resolve(fname));
                documentPath = fname;
            }
            SchoolLevel level = (schoolLevel == null || schoolLevel.isBlank()) ? null : SchoolLevel.valueOf(schoolLevel);
            userService.register(name, email, password, address, phone, School.of(school),
                    wantsStudent, level, documentNo, documentPath);
            return "redirect:/login?kayit";
        } catch (IllegalArgumentException ex) {
            discard(documentPath);
            model.addAttribute("error", ex.getMessage());
            Map<String, String> form = new HashMap<>();
            form.put("name", name);
            form.put("email", email);
            form.put("address", address);
            form.put("phone", phone);
            form.put("school", school);
            model.addAttribute("form", form);
            return "register";
        } catch (Exception ex) {
            discard(documentPath);
            model.addAttribute("error", "Kayıt sırasında bir hata oluştu: " + ex.getMessage());
            return "register";
        }
    }

    /** Kayıt tamamlanamadıysa diske yazılan belgeyi geride bırakma. */
    private void discard(String fileName) {
        if (fileName == null) return;
        try {
            Files.deleteIfExists(uploadDir.resolve(fileName));
        } catch (Exception ignored) {
            // temizlik başarısız olsa da kullanıcıya dönen hata mesajı önemli
        }
    }
}
