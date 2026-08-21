package app.kitapla.web;

import app.kitapla.domain.User;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.QuotaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountController {

    private final QuotaService quotaService;

    public AccountController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/panom")
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("user", user);
        model.addAttribute("quota", quotaService.quotaFor(user));
        return "panom";
    }
}
