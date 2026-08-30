package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "donations")
@Getter
@Setter
public class Donation {

    /** Öğrenci önceliği penceresi (saat) */
    public static final long PRIORITY_WINDOW_HOURS = 48;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User donor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'PURCHASE'")
    private DonationSource source = DonationSource.PURCHASE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'HEPSI'")
    private TargetLevel targetLevel = TargetLevel.HEPSI;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'OPEN'")
    private DonationStatus status = DonationStatus.OPEN;

    /** Bağışçının önerdiği teslim noktası; taraflar mesajlaşarak değiştirebilir. */
    @ManyToOne(fetch = FetchType.LAZY)
    private PickupPoint preferredPoint;

    /** Listede olmayan bir yer önerildiyse. */
    @Column(length = 300)
    private String preferredPointNote;

    @Transient
    public String getPreferredPlaceText() {
        if (preferredPoint != null && preferredPointNote != null && !preferredPointNote.isBlank())
            return preferredPoint.getFullName() + " · " + preferredPointNote;
        if (preferredPoint != null) return preferredPoint.getFullName();
        return preferredPointNote;
    }

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /**
     * Şablonlar bu alanları her satırda kullanır ({@code targetLevel.name()},
     * {@code createdAt.plus(...)}). Eski ya da elle düzeltilmiş bir kayıtta biri boşsa
     * tek satır yüzünden Keşfet'in tamamı 500 veriyordu; boş değer artık güvenli
     * varsayılana düşer.
     */
    public TargetLevel getTargetLevel() {
        return targetLevel == null ? TargetLevel.HEPSI : targetLevel;
    }

    public DonationStatus getStatus() {
        return status == null ? DonationStatus.OPEN : status;
    }

    public DonationSource getSource() {
        return source == null ? DonationSource.OWN : source;
    }

    public Instant getCreatedAt() {
        return createdAt == null ? Instant.EPOCH : createdAt;
    }

    @Transient
    public Instant getPriorityUntil() {
        return getCreatedAt().plus(Duration.ofHours(PRIORITY_WINDOW_HOURS));
    }

    @Transient
    public boolean isPriorityActive() {
        return Instant.now().isBefore(getPriorityUntil());
    }
}
