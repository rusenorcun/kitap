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
    private final app.kitapla.service.DocumentService documentService;

    public AuthController(UserService userService, LoginAttemptService attempts, Features features,
                          app.kitapla.service.DocumentService documentService) {
        this.userService = userService;
        this.attempts = attempts;
        this.features = features;
        this.documentService = documentService;
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
                documentPath = documentService.save(document);
            }
            SchoolLevel level = (schoolLevel == null || schoolLevel.isBlank()) ? null : SchoolLevel.valueOf(schoolLevel.trim().toUpperCase(java.util.Locale.ROOT));
            userService.register(name, email, password, address, phone, School.of(school),
                    wantsStudent, level, documentNo, documentPath);
            return "redirect:/login?kayit";
        } catch (IllegalArgumentException ex) {
            documentService.discard(documentPath);
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
            documentService.discard(documentPath);
            model.addAttribute("error", "Kayıt sırasında bir hata oluştu: " + ex.getMessage());
            return "register";
        }
    }
}
