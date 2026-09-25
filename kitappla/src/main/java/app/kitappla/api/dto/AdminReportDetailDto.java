package app.kitappla.api.dto;

import java.util.List;

public record AdminReportDetailDto(
        AdminReportDto report,
        String entityTitle,
        String entitySubtitle,
        String entityDetails,
        String entityStatus,
        String entityExtra,
        List<ChatMessageDto> moderationMessages,
        List<ChatMessageDto> supportMessages,
        Long supportConversationId
) {}
