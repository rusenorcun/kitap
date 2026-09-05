package app.kitapla.api.dto;

import java.util.List;

public record NotificationsResponse(
        List<NotificationDto> items,
        long unread
) {}
