package app.kitapla.api.v1;

import app.kitapla.api.dto.*;
import app.kitapla.config.Features;
import app.kitapla.domain.*;
import app.kitapla.repo.ClaimRepository;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DonationApiController {

    private final DonationService donationService;
    private final BookService bookService;
    private final ClaimRepository claimRepository;
    private final MessageService messageService;
    private final Features features;

    public DonationApiController(DonationService donationService,
                                 BookService bookService,
                                 ClaimRepository claimRepository,
                                 MessageService messageService,
                                 Features features) {
        this.donationService = donationService;
        this.bookService = bookService;
        this.claimRepository = claimRepository;
        this.messageService = messageService;
        this.features = features;
    }

    @GetMapping("/donations")
    public ResponseEntity<List<DonationDto>> openDonations(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "true") boolean available) {

        TargetLevel targetLevel = null;
        if (level != null && !level.isBlank()) {
            try {
                targetLevel = TargetLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        DonationService.Filter filter = new DonationService.Filter(targetLevel, q, null, available);
        List<DonationView> views = donationService.openDonations(filter);
        User me = CurrentUser.get();

        List<DonationDto> dtos = views.stream()
                .map(v -> {
                    ClaimEligibility eligibility = me != null ? donationService.eligibility(v, me) : null;
                    return ApiDtoMapper.toDonationDto(v, eligibility);
                })
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/donations/{id}")
    public ResponseEntity<DonationDto> getDonation(@PathVariable Long id) {
        DonationView view = donationService.view(id)
                .orElseThrow(() -> new IllegalStateException("Bağış bulunamadı."));
        User me = CurrentUser.get();
        ClaimEligibility eligibility = me != null ? donationService.eligibility(view, me) : null;
        return ResponseEntity.ok(ApiDtoMapper.toDonationDto(view, eligibility));
    }

    @PostMapping("/donations")
    public ResponseEntity<IdStatusDto> createDonation(@Valid @RequestBody CreateDonationBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Book book = bookService.findOrCreate(
                body.title(),
                body.author(),
                body.purchaseLink(),
                body.coverUrl(),
                body.description(),
                me.getId()
        );

        TargetLevel targetLevel = TargetLevel.HEPSI;
        if (body.targetLevel() != null && !body.targetLevel().isBlank()) {
            try {
                targetLevel = TargetLevel.valueOf(body.targetLevel().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        DonationSource source = DonationSource.OWN;
        if (body.source() != null && !body.source().isBlank()) {
            try {
                source = DonationSource.valueOf(body.source().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        Donation d = donationService.create(
                me,
                book,
                body.quantity(),
                targetLevel,
                source,
                body.description(),
                body.pointId(),
                body.pointNote()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IdStatusDto(d.getId(), d.getStatus().name()));
    }

    @PostMapping("/donations/{id}/claim")
    public ResponseEntity<ClaimDto> claim(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Claim claim = donationService.claim(id, me);
        Long convId = messageService.find(ConversationKind.CLAIM, claim.getId())
                .map(Conversation::getId).orElse(null);

        return ResponseEntity.ok(ApiDtoMapper.toClaimDto(claim, features.isAddress() || features.isShipping(), convId));
    }

    @PostMapping("/donations/{id}/close")
    public ResponseEntity<Void> closeDonation(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.close(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/donations/{id}/reopen")
    public ResponseEntity<Void> reopenDonation(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.reopen(id, me);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/donations/{id}")
    public ResponseEntity<Void> deleteDonation(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.delete(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/donations/{id}/to-swap")
    public ResponseEntity<IdStatusDto> moveToSwap(@PathVariable Long id, @RequestBody(required = false) CreateSwapBookBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        String note = body != null ? body.note() : null;
        SwapBook sb = donationService.moveToSwap(id, me, note);
        return ResponseEntity.ok(new IdStatusDto(sb.getId(), sb.getStatus().name()));
    }

    @GetMapping("/my/donations")
    public ResponseEntity<List<MyDonationDto>> myDonations() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<DonationView> views = donationService.myDonations(me);
        List<MyDonationDto> dtos = views.stream().map(v -> {
            List<Claim> claims = claimRepository.findByDonationWithStudent(v.donation());
            List<ClaimDto> claimDtos = claims.stream().map(c -> {
                Long convId = messageService.find(ConversationKind.CLAIM, c.getId())
                        .map(Conversation::getId).orElse(null);
                return ApiDtoMapper.toClaimDto(c, features.isAddress() || features.isShipping(), convId);
            }).toList();
            return ApiDtoMapper.toMyDonationDto(v, claimDtos);
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my/claims")
    public ResponseEntity<List<MyClaimDto>> myClaims() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<Claim> claims = claimRepository.findByStudentWithDetails(me);
        List<MyClaimDto> dtos = claims.stream().map(c -> {
            Long convId = messageService.find(ConversationKind.CLAIM, c.getId())
                    .map(Conversation::getId).orElse(null);
            return ApiDtoMapper.toMyClaimDto(c, convId);
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/claims/{id}/ship")
    public ResponseEntity<Void> shipClaim(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.ship(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/meeting")
    public ResponseEntity<Void> arrangeMeeting(@PathVariable Long id, @Valid @RequestBody ArrangeMeetingBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Instant at = Instant.parse(body.at());
        MeetingRequest req = new MeetingRequest(body.pointId(), body.note(), at);
        donationService.arrange(id, me, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.noShow(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/deliver")
    public ResponseEntity<Void> deliverClaim(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.deliver(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/thank")
    public ResponseEntity<Void> thankClaim(@PathVariable Long id, @RequestBody(required = false) ThankBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        String message = body != null ? body.message() : null;
        donationService.thank(id, me, message);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/cancel")
    public ResponseEntity<Void> cancelClaim(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        donationService.cancelClaim(id, me);
        return ResponseEntity.noContent().build();
    }
}
