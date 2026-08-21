package app.kitapla.web;

import app.kitapla.domain.SchoolLevel;
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
    private final Path uploadDir;

    public AuthController(UserService userService, @Value("${kitapla.upload-dir}") String uploadDir) {
        this.userService = userService;
        this.uploadDir = Path.of(uploadDir, "documents");
    }

    @GetMapping("/login")
    public String login() {
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
                           @RequestParam(required = false, defaultValue = "false") boolean wantsStudent,
                           @RequestParam(required = false) String schoolLevel,
                           @RequestParam(required = false) String documentNo,
                           @RequestParam(required = false) MultipartFile document,
                           Model model) {
        try {
            String documentPath = null;
            if (wantsStudent && document != null && !document.isEmpty()) {
                Files.createDirectories(uploadDir);
                String safe = document.getOriginalFilename() == null ? "belge"
                        : document.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
                String fname = System.currentTimeMillis() + "-" + safe;
                document.transferTo(uploadDir.resolve(fname));
                documentPath = fname;
            }
            SchoolLevel level = (schoolLevel == null || schoolLevel.isBlank()) ? null : SchoolLevel.valueOf(schoolLevel);
            userService.register(name, email, password, address, phone, wantsStudent, level, documentNo, documentPath);
            return "redirect:/login?kayit";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            Map<String, String> form = new HashMap<>();
            form.put("name", name);
            form.put("email", email);
            form.put("address", address);
            form.put("phone", phone);
            model.addAttribute("form", form);
            return "register";
        } catch (Exception ex) {
            model.addAttribute("error", "Kayıt sırasında bir hata oluştu: " + ex.getMessage());
            return "register";
        }
    }
}
