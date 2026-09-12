package app.kitapla.api.v1;

import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.MyReportDto;
import app.kitapla.api.dto.ReportBody;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.ReportKind;
import app.kitapla.domain.ReportReason;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.MessageService;
import app.kitapla.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportApiController {

    private final ReportService reportService;
    private final MessageService messageService;

    public ReportApiController(ReportService reportService, MessageService messageService) {
        this.reportService = reportService;
        this.messageService = messageService;
    }

    /**
     * Kullanıcının kendi şikâyetleri ve sonuçları.
     * <p>
     * Şikâyet göndermekle iş bitmiyor: kullanıcı durumu görebilmeli ve destek
     * sohbetine buradan girebilmeli. {@code conversationId} yalnızca sohbet
     * daha önce açıldıysa dolu gelir; boşsa istemci /conversations/open ile açar.
     */
    @GetMapping
    public ResponseEntity<java.util.List<MyReportDto>> mine() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        return ResponseEntity.ok(reportService.mine(me).stream()
                .map(r -> ApiDtoMapper.toMyReportDto(r,
                        messageService.find(ConversationKind.REPORT, r.getId())
                                .map(app.kitapla.domain.Conversation::getId).orElse(null)))
                .toList());
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
