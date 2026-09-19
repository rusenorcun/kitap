package app.kitapla.api.v1;

import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.NotificationDto;
import app.kitapla.api.dto.NotificationsResponse;
import app.kitapla.domain.Notification;
import app.kitapla.domain.User;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationsResponse> getNotifications() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        List<Notification> list = notificationService.latest(me);
        long unread = notificationService.unreadCount(me);
        List<NotificationDto> dtos = list.stream().map(ApiDtoMapper::toNotificationDto).toList();

        return ResponseEntity.ok(new NotificationsResponse(dtos, unread));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> readAll() {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        int count = notificationService.markAllRead(me);
        return ResponseEntity.ok(Map.of("updated", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> readOne(@PathVariable Long id) {
        User me = CurrentUser.get();
        if (me == null) throw new IllegalStateException("Giriş yapmalısınız.");

        notificationService.markRead(me, id);
        return ResponseEntity.noContent().build();
    }
}
