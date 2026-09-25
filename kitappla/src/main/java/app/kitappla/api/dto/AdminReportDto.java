package app.kitappla.api.dto;

/**
 * Yönetici tarafından incelenen şikâyet kaydı DTO'su.
 */
public record AdminReportDto(
        Long id,
        String kind,
        String kindLabel,
        Long refId,
        String reason,
        String reasonLabel,
        String note,
        String status,
        String adminNote,
        Long reporterId,
        String reporterName,
        String reporterEmail,
        Long reportedUserId,
        String reportedUserName,
        String reportedUserEmail,
        boolean reportedUserBlocked,
        String createdAt,
        String reviewedAt
) {}
