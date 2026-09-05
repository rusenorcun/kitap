package app.kitapla.api.v1;

import app.kitapla.api.dto.ReportBody;
import app.kitapla.domain.ReportKind;
import app.kitapla.domain.ReportReason;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{kind}/{refId}")
    public ResponseEntity<Void> report(@PathVariable String kind,
                                       @PathVariable Long refId,
                                       @Valid @RequestBody ReportBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        ReportKind reportKind = switch (kind.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "conversation" -> ReportKind.CONVERSATION;
            case "donation" -> ReportKind.DONATION;
            case "request" -> ReportKind.REQUEST;
            case "claim", "delivery" -> ReportKind.CLAIM;
            case "swap_book", "swapbook" -> ReportKind.SWAP_BOOK;
            case "swap_offer", "swapoffer", "swap" -> ReportKind.SWAP_OFFER;
            case "user" -> ReportKind.USER;
            default -> throw new IllegalArgumentException("Geçersiz şikâyet türü: " + kind);
        };

        ReportReason reason = ReportReason.valueOf(body.reason().trim().toUpperCase(java.util.Locale.ROOT));
        reportService.create(me, reportKind, refId, reason, body.note());
        return ResponseEntity.noContent().build();
    }
}
