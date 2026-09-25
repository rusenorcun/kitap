package app.kitappla.api.v1;

import org.springframework.data.domain.Page;
import java.util.Map;
import app.kitappla.api.dto.*;
import app.kitappla.config.Features;
import app.kitappla.domain.*;
import app.kitappla.security.CurrentUser;
import app.kitappla.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SwapApiController {

    private final SwapService swapService;
    private final BookService bookService;
    private final MessageService messageService;
    private final Features features;

    public SwapApiController(SwapService swapService,
                             BookService bookService,
                             MessageService messageService,
                             Features features) {
        this.swapService = swapService;
        this.bookService = bookService;
        this.messageService = messageService;
        this.features = features;
    }

    @GetMapping("/swap/discover")
    public ResponseEntity<List<SwapListingDto>> discover(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false, defaultValue = "24") int size) {
        Page<SwapBook> sayfa = swapService.discover(CurrentUser.get(), q, Sayfalama.of(page, size));
        return ResponseEntity.ok()
                .header(Sayfalama.TOPLAM_BASLIGI, String.valueOf(sayfa.getTotalElements()))
                .body(sayfa.getContent().stream().map(ApiDtoMapper::toSwapListingDto).toList());
    }

    @GetMapping("/swap/my-books")
    public ResponseEntity<List<SwapListingDto>> myBooks() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<SwapBook> list = swapService.myBooks(me);
        List<SwapListingDto> dtos = list.stream().map(ApiDtoMapper::toSwapListingDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/swap/books")
    public ResponseEntity<IdStatusDto> addSwapBook(@Valid @RequestBody CreateSwapBookBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Book book = bookService.findOrCreate(
                body.title(),
                body.author(),
                body.purchaseLink(),
                body.coverUrl(),
                null,
                me.getId()
        );

        SwapBook sb = swapService.open(me, book, body.note());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IdStatusDto(sb.getId(), sb.getStatus().name()));
    }

    @PostMapping("/swap/books/{id}/status")
    public ResponseEntity<Void> setSwapBookStatus(@PathVariable Long id, @Valid @RequestBody SwapStatusBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        SwapBookStatus status = SwapBookStatus.valueOf(body.status().trim().toUpperCase(java.util.Locale.ROOT));
        swapService.setStatus(id, me, status);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/swap/books/{id}")
    public ResponseEntity<Void> removeSwapBook(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        swapService.removeBook(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/swap/books/{id}/to-donation")
    public ResponseEntity<IdStatusDto> moveToDonation(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        Donation d = swapService.moveToDonation(id, me, TargetLevel.HEPSI, null, null, null);
        return ResponseEntity.ok(new IdStatusDto(d.getId(), d.getStatus().name()));
    }

    /** Teklifleri DTO'ya çevirir; sohbet kimlikleri teklif başına değil, tek sorguda okunur. */
    private List<OfferDto> toOfferDtos(List<SwapOffer> list, User me) {
        Map<Long, Long> sohbetler = messageService.conversationIds(ConversationKind.SWAP,
                list.stream().map(SwapOffer::getId).toList());
        return list.stream()
                .map(o -> ApiDtoMapper.toOfferDto(o, me, sohbetler.get(o.getId()), features.isShipping()))
                .toList();
    }

    @GetMapping("/swaps/incoming")
    public ResponseEntity<List<OfferDto>> incomingOffers() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<SwapOffer> list = swapService.incoming(me);
        return ResponseEntity.ok(toOfferDtos(list, me));
    }

    @GetMapping("/swaps/outgoing")
    public ResponseEntity<List<OfferDto>> outgoingOffers() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<SwapOffer> list = swapService.outgoing(me);
        return ResponseEntity.ok(toOfferDtos(list, me));
    }

    @PostMapping("/swaps")
    public ResponseEntity<IdStatusDto> createOffer(@Valid @RequestBody CreateOfferBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        SwapOffer offer = swapService.offer(body.targetBookId(), body.offeredBookId(), me, body.message());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IdStatusDto(offer.getId(), offer.getStatus().name()));
    }

    @PostMapping("/swaps/{id}/accept")
    public ResponseEntity<IdStatusDto> acceptOffer(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        SwapOffer offer = swapService.accept(id, me);
        return ResponseEntity.ok(new IdStatusDto(offer.getId(), offer.getStatus().name()));
    }

    @PostMapping("/swaps/{id}/reject")
    public ResponseEntity<Void> rejectOffer(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        swapService.reject(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/swaps/{id}/cancel")
    public ResponseEntity<Void> cancelOffer(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        swapService.cancel(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = {"/swaps/{id}/handover", "/swaps/{id}/ship"})
    public ResponseEntity<Void> handoverOffer(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        swapService.ship(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/swaps/{id}/meeting")
    public ResponseEntity<Void> arrangeMeeting(@PathVariable Long id, @Valid @RequestBody ArrangeMeetingBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Instant at = Instant.parse(body.at());
        MeetingRequest req = new MeetingRequest(body.pointId(), body.note(), at);
        swapService.arrange(id, me, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/swaps/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        swapService.noShow(id, me);
        return ResponseEntity.noContent().build();
    }
}
