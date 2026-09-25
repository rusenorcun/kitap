package app.kitappla.web;

import app.kitappla.domain.User;
import app.kitappla.security.CurrentUser;
import app.kitappla.security.EpostaDogrulamaFiltresi;
import app.kitappla.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Hesap e-postası doğrulama: bağlantı sayfası, bekleyen doğrulama sayfası ve yeniden gönderme. */
@Controller
public class EpostaDogrulamaController {

    private final UserService userService;

    public EpostaDogrulamaController(UserService userService) {
        this.userService = userService;
    }

    /**
     * E-postadaki bağlantı. Oturum gerektirmez; tıklamak hesabı doğrular.
     *
     * @param uygulama 1 ise sayfa kullanıcıyı uygulamaya geri gönderir (mobil kayıtların
     *                 {@code /uygulamada-ac/eposta-dogrula} öncesinde gönderilmiş bağlantıları)
     */
    @GetMapping("/eposta-dogrula")
    public String dogrula(@RequestParam(required = false) String token,
                          @RequestParam(required = false) String uygulama,
                          Model model) {
        model.addAttribute("sonuc", userService.confirmAccountEmail(token).name());
        model.addAttribute("uygulama", "1".equals(uygulama));
        return "eposta-dogrula";
    }

    /**
     * Mobil uygulamadan başlatılan kaydın bağlantısı. Uygulama yüklüyse Android bağlantıyı App Link
     * olarak uygulamaya verir, onayı uygulama yapar ({@code POST /api/v1/auth/verify-email}); bu sayfa
     * yalnızca bağlantı tarayıcıda açılınca (uygulama yok, masaüstü) görülür ve onayı kendisi yapar.
     */
    @GetMapping("/uygulamada-ac/eposta-dogrula")
    public String dogrulaUygulamada(@RequestParam(required = false) String token, Model model) {
        return dogrula(token, "1", model);
    }

    /**
     * Mobil uygulamadan istenen okul e-postası doğrulamasının bağlantısı. Okul adresi onayı oturum
     * ister; tarayıcıda oturum olmadığı için kullanıcı jetonla birlikte uygulamaya gönderilir,
     * onayı uygulama yapar. Uygulama yoksa web onay sayfası (girişten sonra) yedek yoldur.
     */
    @GetMapping("/uygulamada-ac/okul-eposta")
    public String okulEpostasiUygulamada(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "uygulamada-ac";
    }

    /** Doğrulanmamış üyenin işlem denediğinde yönlendirildiği sayfa. */
    @GetMapping(EpostaDogrulamaFiltresi.SAYFA)
    public String bekleyen(Model model) {
        User user = CurrentUser.get();
        if (user == null) return "redirect:/login";
        if (user.isEmailVerified()) return "redirect:/panom";
        model.addAttribute("user", user);
        return "hesap-dogrulama";
    }

    @PostMapping(EpostaDogrulamaFiltresi.SAYFA + "/yeniden")
    public String yenidenGonder(RedirectAttributes ra) {
        User user = CurrentUser.get();
        if (user == null) return "redirect:/login";
        try {
            userService.resendAccountVerification(user, false);
            ra.addFlashAttribute("basari", user.getEmail()
                    + " adresine yeni bir doğrulama bağlantısı gönderdik. Gelmezse gereksiz (spam) klasörüne bak.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("hata", ex.getMessage());
        }
        return "redirect:" + EpostaDogrulamaFiltresi.SAYFA;
    }
}
