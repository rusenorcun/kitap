package app.kitapla.api.dto;

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
        Long reportedUserId,
        String reportedUserName,
        String createdAt,
        String reviewedAt
) {}
