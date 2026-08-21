package app.kitapla.web;

import app.kitapla.domain.ClaimStatus;
import app.kitapla.domain.OfferStatus;
import app.kitapla.domain.RequestStatus;
import app.kitapla.repo.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class HomeController {

    private final DonationRepository donations;
    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;
    private final UserRepository users;

    public HomeController(DonationRepository donations, ClaimRepository claims, BookRequestRepository requests,
                          SwapOfferRepository offers, UserRepository users) {
        this.donations = donations;
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
        this.users = users;
    }

    @GetMapping("/")
    public String index(Model model) {
        long delivered = claims.countByStatus(ClaimStatus.DELIVERED)
                + requests.countByStatus(RequestStatus.DELIVERED);
        model.addAttribute("stats", Map.of(
                "donations", donations.count(),
                "delivered", delivered,
                "swaps", offers.countByStatus(OfferStatus.COMPLETED),
                "members", users.count()
        ));
        return "index";
    }

    @GetMapping("/sss")
    public String faq() {
        return "sss";
    }
}
