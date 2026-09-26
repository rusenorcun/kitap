package app.kitappla.api.v1;

import org.springframework.data.domain.Page;
import java.util.Map;
import app.kitappla.api.dto.*;
import app.kitappla.domain.Book;
import app.kitappla.domain.BookRequest;
import app.kitappla.domain.ConversationKind;
import app.kitappla.domain.DonationSource;
import app.kitappla.domain.User;
import app.kitappla.security.CurrentUser;
import app.kitappla.service.BookService;
import app.kitappla.service.MeetingRequest;
import app.kitappla.service.MessageService;
import app.kitappla.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RequestApiController {

    private final RequestService requestService;
    private final BookService bookService;
    private final MessageService messageService;

    public RequestApiController(RequestService requestService,
                                BookService bookService,
                                MessageService messageService) {
        this.requestService = requestService;
        this.bookService = bookService;
        this.messageService = messageService;
    }

    /** İstekleri DTO'ya çevirir; sohbet kimlikleri istek başına değil, tek sorguda okunur. */
    private List<RequestDto> toDtos(List<BookRequest> list) {
        Map<Long, Long> sohbetler = messageService.conversationIds(ConversationKind.REQUEST,
                list.stream().map(BookRequest::getId).toList());
        return list.stream().map(r -> ApiDtoMapper.toRequestDto(r, sohbetler.get(r.getId()))).toList();
    }

    @GetMapping("/requests/open")
    public ResponseEntity<List<RequestDto>> openRequests(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false, defaultValue = "24") int size) {
        Page<BookRequest> sayfa = requestService.openRequests(q, Sayfalama.of(page, size));
        return ResponseEntity.ok()
                .header(Sayfalama.TOPLAM_BASLIGI, String.valueOf(sayfa.getTotalElements()))
                .body(toDtos(sayfa.getContent()));
    }

    @GetMapping("/my/requests")
    public ResponseEntity<List<RequestDto>> myRequests() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<BookRequest> list = requestService.myRequests(me);
        List<RequestDto> dtos = toDtos(list);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my/fulfilled")
    public ResponseEntity<List<RequestDto>> fulfilledByMe() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<BookRequest> list = requestService.fulfilledByMe(me);
        List<RequestDto> dtos = toDtos(list);
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/requests")
    public ResponseEntity<IdStatusDto> createRequest(@Valid @RequestBody CreateRequestBody body) {
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

        BookRequest request = requestService.create(me, book, body.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IdStatusDto(request.getId(), request.getStatus().name()));
    }

    @PostMapping("/requests/{id}/fulfill")
    public ResponseEntity<IdStatusDto> fulfillRequest(@PathVariable Long id, @RequestBody(required = false) FulfillBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        DonationSource source = DonationSource.OWN;
        if (body != null && body.source() != null && !body.source().isBlank()) {
            try {
                source = DonationSource.valueOf(body.source().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        BookRequest request = requestService.fulfill(id, me, source);
        return ResponseEntity.ok(new IdStatusDto(request.getId(), request.getStatus().name()));
    }

    @PostMapping("/requests/{id}/cancel-fulfillment")
    public ResponseEntity<Void> cancelFulfillment(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        requestService.cancelFulfillment(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/ship")
    public ResponseEntity<Void> shipRequest(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        requestService.ship(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/meeting")
    public ResponseEntity<Void> arrangeMeeting(@PathVariable Long id, @Valid @RequestBody ArrangeMeetingBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Instant at = Instant.parse(body.at());
        MeetingRequest req = new MeetingRequest(body.pointId(), body.note(), at);
        requestService.arrange(id, me, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        requestService.noShow(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/deliver")
    public ResponseEntity<Void> deliverRequest(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        requestService.deliver(id, me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/thank")
    public ResponseEntity<Void> thankRequest(@PathVariable Long id, @RequestBody(required = false) ThankBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        String message = body != null ? body.message() : null;
        requestService.thank(id, me, message);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");
        requestService.delete(id, me);
        return ResponseEntity.noContent().build();
    }
}
