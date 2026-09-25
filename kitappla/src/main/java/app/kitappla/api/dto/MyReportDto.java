package app.kitappla.api.dto;

/**
 * Kullanıcının kendi gönderdiği şikâyet.
 * <p>
 * Şikâyet edilen kişinin kimliği burada dönmez: şikâyet eden zaten kimi
 * bildirdiğini biliyor, ama bunu API'de taşımak gereksiz kişisel veri olur.
 */
public record MyReportDto(
        Long id,
        /** CONVERSATION | DONATION | CLAIM | REQUEST | SWAP_BOOK | SWAP_OFFER | USER */
        String kind,
        String kindLabel,
        Long refId,
        String reason,
        String reasonLabel,
        String note,
        /** OPEN | ACTIONED | DISMISSED */
        String status,
        /** Sonuçlandıysa yönetimin şikâyet edene ilettiği not. */
        String adminNote,
        /** Destek sohbeti açıldıysa kimliği; yoksa /conversations/open ile açılır. */
        Long conversationId,
        String createdAt,
        String reviewedAt
) {}
