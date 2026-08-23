package app.kitapla.web;

import app.kitapla.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Şifremi unuttum / şifre sıfırlama sayfaları. */
@Controller
public class PasswordResetController {

    /** Adres kayıtlı olsun ya da olmasın aynı mesaj gösterilir (hesap sızdırmamak için). */
    private static final String AYNI_CEVAP =
            "Bu adres kayıtlıysa şifre sıfırlama bağlantısı gönderildi. Gelen kutunu kontrol et.";

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @GetMapping("/sifremi-unuttum")
    public String form() {
        return "sifremi-unuttum";
    }

    @PostMapping("/sifremi-unuttum")
    public String request(@RequestParam(required = false) String email, RedirectAttributes ra) {
        try {
            service.request(email);
            ra.addFlashAttribute("basari", AYNI_CEVAP);
        } catch (IllegalStateException ex) {
            // Yalnızca istek sınırı aşıldığında oluşur
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:/sifremi-unuttum";
    }

    @GetMapping("/sifre-sifirla")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("gecerli", service.isValid(token));
        return "sifre-sifirla";
    }

    @PostMapping("/sifre-sifirla")
    public String reset(@RequestParam(required = false) String token,
                        @RequestParam(required = false) String newPassword,
                        @RequestParam(required = false) String confirmPassword,
                        RedirectAttributes ra) {
        try {
            service.reset(token, newPassword, confirmPassword);
            ra.addFlashAttribute("basari", "Şifren güncellendi. Şimdi giriş yapabilirsin.");
            return "redirect:/login";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
            return "redirect:/sifre-sifirla?token=" + (token == null ? "" : token);
        }
    }
}
