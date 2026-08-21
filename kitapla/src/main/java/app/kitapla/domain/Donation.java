package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private DonationSource source = DonationSource.PURCHASE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetLevel targetLevel = TargetLevel.HEPSI;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationStatus status = DonationStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public Instant getPriorityUntil() {
        return createdAt.plus(Duration.ofHours(PRIORITY_WINDOW_HOURS));
    }

    @Transient
    public boolean isPriorityActive() {
        return Instant.now().isBefore(getPriorityUntil());
    }
}
