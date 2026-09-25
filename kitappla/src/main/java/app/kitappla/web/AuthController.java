package app.kitappla.web;

import app.kitappla.config.Features;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import app.kitappla.domain.School;
import app.kitappla.domain.SchoolLevel;
import app.kitappla.security.LoginAttemptService;
import app.kitappla.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final LoginAttemptService attempts;
    private final Features features;
    private final app.kitappla.service.DocumentService documentService;

    public AuthController(UserService userService, LoginAttemptService attempts, Features features,
                          app.kitappla.service.DocumentService documentService) {
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
                            Model model,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        // Belgeli başvuru kapalıyken kayıt formunda bu alanlar hiç gösterilmez;
        // gönderilseler bile dikkate alınmazlar.
        if (!features.isDocument()) wantsStudent = false;

        String documentPath = null;
        try {
            if (wantsStudent && document != null && !document.isEmpty()) {
                documentPath = documentService.save(document);
            }
            SchoolLevel level = null;
            if (schoolLevel != null && !schoolLevel.isBlank()) {
                try {
                    level = SchoolLevel.valueOf(schoolLevel.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
            var registered = userService.register(name, email, password, address, phone, School.of(school),
                    wantsStudent, level, documentNo, documentPath);
            String mesaj = userService.registrationMessage(registered);
            if (mesaj != null) ra.addFlashAttribute("basari", mesaj);
            return "redirect:/login?kayit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
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
            // İç hata metni kayıt formunda gösterilmez: dosya yolu, kısıt adı gibi
            // ayrıntıları kimliksiz bir ziyaretçiye sızdırıyordu. Ayrıntı günlüğe gider.
            log.error("Kayıt sırasında beklenmeyen hata", ex);
            documentService.discard(documentPath);
            model.addAttribute("error", "Kayıt sırasında beklenmeyen bir hata oluştu. Lütfen tekrar dene.");
            return "register";
        }
    }
}
