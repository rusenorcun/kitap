package app.kitapla.api.v1;

import app.kitapla.api.dto.*;
import app.kitapla.domain.Book;
import app.kitapla.domain.BookRequest;
import app.kitapla.domain.Conversation;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.DonationSource;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.BookService;
import app.kitapla.service.MeetingRequest;
import app.kitapla.service.MessageService;
import app.kitapla.service.RequestService;
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

    @GetMapping("/requests/open")
    public ResponseEntity<List<RequestDto>> openRequests(@RequestParam(required = false) String q) {
        List<BookRequest> list = requestService.openRequests(q);
        List<RequestDto> dtos = list.stream().map(r -> {
            Long convId = messageService.find(ConversationKind.REQUEST, r.getId())
                    .map(Conversation::getId).orElse(null);
            return ApiDtoMapper.toRequestDto(r, convId);
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my/requests")
    public ResponseEntity<List<RequestDto>> myRequests() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<BookRequest> list = requestService.myRequests(me);
        List<RequestDto> dtos = list.stream().map(r -> {
            Long convId = messageService.find(ConversationKind.REQUEST, r.getId())
                    .map(Conversation::getId).orElse(null);
            return ApiDtoMapper.toRequestDto(r, convId);
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my/fulfilled")
    public ResponseEntity<List<RequestDto>> fulfilledByMe() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<BookRequest> list = requestService.fulfilledByMe(me);
        List<RequestDto> dtos = list.stream().map(r -> {
            Long convId = messageService.find(ConversationKind.REQUEST, r.getId())
                    .map(Conversation::getId).orElse(null);
            return ApiDtoMapper.toRequestDto(r, convId);
        }).toList();
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
                null,
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
