package app.kitappla.web;

import app.kitappla.service.PasswordResetService;
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
        // Sonuç ne olursa olsun tek cevap: istek sınırı bile farklı bir mesajla
        // dönerse adresin kayıtlı olduğu anlaşılırdı.
        service.request(email);
        ra.addFlashAttribute("basari", AYNI_CEVAP);
        return "redirect:/sifremi-unuttum";
    }

    @GetMapping("/sifre-sifirla")
    public String resetForm(@RequestParam(required = false) String token,
                            @RequestParam(required = false) String uygulama, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("uygulama", "1".equals(uygulama));
        model.addAttribute("gecerli", service.isValid(token));
        return "sifre-sifirla";
    }

    /**
     * Mobil uygulamadan istenen sıfırlamanın bağlantısı. Uygulama yüklüyse Android bağlantıyı App Link
     * olarak uygulamaya verir; bu sayfa yalnızca bağlantı tarayıcıda açılınca görülür: uygulamada açmayı
     * önerir, web formu yedek olarak durur.
     */
    @GetMapping("/uygulamada-ac/sifre-sifirla")
    public String resetFormUygulamada(@RequestParam(required = false) String token, Model model) {
        return resetForm(token, "1", model);
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
