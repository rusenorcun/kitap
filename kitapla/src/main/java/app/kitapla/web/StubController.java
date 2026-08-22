package app.kitapla.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Henüz yapılmamış sayfalar için geçici yer tutucu (sonraki turlarda gerçek sayfalarla değişecek). */
@Controller
public class StubController {

    private String stub(Model model, String active, String baslik) {
        model.addAttribute("active", active);
        model.addAttribute("baslik", baslik);
        return "yakinda";
    }

    @GetMapping("/isteklerim") public String istekler(Model m) { return stub(m, "istekler", "İsteklerim"); }
    @GetMapping("/takas")      public String takas(Model m)    { return stub(m, "takas", "Takas"); }
    @GetMapping("/bildirimler")public String bildirim(Model m) { return stub(m, "", "Bildirimler"); }
    @GetMapping("/profil")     public String profil(Model m)   { return stub(m, "", "Profil"); }
}
