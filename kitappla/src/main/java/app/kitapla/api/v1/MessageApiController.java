package app.kitapla.api.v1;

import java.util.Map;
import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.ChatMessageDto;
import app.kitapla.api.dto.ConversationDto;
import app.kitapla.api.dto.SendMessageBody;
import app.kitapla.domain.Conversation;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.Message;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
public class MessageApiController {

    private final MessageService messageService;
    private final app.kitapla.config.Marka marka;

    public MessageApiController(MessageService messageService, app.kitapla.config.Marka marka) {
        this.messageService = messageService;
        this.marka = marka;
    }

    @GetMapping
    public ResponseEntity<List<ConversationDto>> conversations() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<Conversation> list = messageService.mine(me);
        Map<Long, Long> okunmamis = messageService.unreadCounts(list, me);
        List<ConversationDto> dtos = list.stream()
                .map(c -> ApiDtoMapper.toConversationDto(c, me, okunmamis.get(c.getId()),
                        marka.destekAdi(), marka.destekKisaltma()))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/open/{kind}/{refId}")
    public ResponseEntity<ConversationDto> openConversation(@PathVariable String kind, @PathVariable Long refId) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        ConversationKind k = ConversationKind.valueOf(kind.trim().toUpperCase(java.util.Locale.ROOT));
        Conversation c = messageService.open(k, refId, me);
        long unread = messageService.unread(c, me);
        return ResponseEntity.ok(ApiDtoMapper.toConversationDto(c, me, unread, marka.destekAdi(), marka.destekKisaltma()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> messages(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Conversation c = messageService.require(id, me);
        messageService.markRead(c, me);

        // Şikâyet sohbetinde yöneticinin gerçek adı şikâyet edene gösterilmez (web ile aynı)
        boolean maskele = c.isYonetimSohbeti() && !me.isAdmin();

        List<Message> msgs = messageService.messagesOf(c);
        List<ChatMessageDto> dtos = msgs.stream()
                .map(m -> ApiDtoMapper.toChatMessageDto(m, me, maskele, marka.destekAdi()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageBody body) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        Message m = messageService.send(id, me, body.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiDtoMapper.toChatMessageDto(m, me));
    }
}
