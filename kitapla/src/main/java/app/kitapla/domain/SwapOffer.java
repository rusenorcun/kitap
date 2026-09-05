package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "swap_offers")
@Getter
@Setter
public class SwapOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User toUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SwapBook offeredSwapBook;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SwapBook targetSwapBook;

    @Column(length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'PENDING'")
    private OfferStatus status = OfferStatus.PENDING;

    @Embedded
    private Meeting meeting = new Meeting();

    /** Kargo modunda "kargoladı", yüz yüze teslimde "teslimi onayladı". */
    private Instant fromShippedAt;
    private Instant toShippedAt;
    private Instant decidedAt;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /**
     * JPA, tüm sütunları null olan gömülü nesneyi null olarak yükler; bu yüzden
     * getter boş bir Meeting üretir. Aksi halde eski kayıtlarda NPE oluşur.
     */
    public Meeting getMeeting() {
        if (meeting == null) meeting = new Meeting();
        return meeting;
    }

    @Transient
    public String getCreatedAtText() {
        if (createdAt == null) return "";
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(createdAt);
    }
}
