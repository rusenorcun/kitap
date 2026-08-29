package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Bir üyenin yaptığı şikâyet.
 * <p>
 * Şikâyet, yöneticinin ilgili içeriği görmesine kapı açar. Özellikle sohbetler
 * için bu önemlidir: yönetici <b>yalnızca şikâyet edilmiş</b> bir sohbetin
 * mesajlarını okuyabilir, diğerleri kapalı kalır (bkz. MessageService).
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "ix_report_status", columnList = "status"),
        @Index(name = "ix_report_target", columnList = "kind, ref_id")
})
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportKind kind;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    /** Şikâyet edenin açıklaması. */
    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.OPEN;

    /** Şikâyet edilen içeriğin sahibi; listede hızlı görünsün diye kaydedilir. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User reportedUser;

    /** İncelendiğinde doldurulur. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User reviewedBy;

    private Instant reviewedAt;

    @Column(length = 1000)
    private String adminNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public String getCreatedAtText() {
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(createdAt);
    }
}
