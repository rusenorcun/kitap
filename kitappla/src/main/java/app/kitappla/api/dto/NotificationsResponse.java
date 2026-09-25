package app.kitappla.api.dto;

import java.util.List;

public record NotificationsResponse(
        List<NotificationDto> items,
        long unread
) {}
