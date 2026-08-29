package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "claims", uniqueConstraints = @UniqueConstraint(columnNames = {"donation_id", "student_id"}))
@Getter
@Setter
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'MATCHED'")
    private ClaimStatus status = ClaimStatus.MATCHED;

    @Embedded
    private Meeting meeting = new Meeting();

    private Instant shippedAt;
    private Instant deliveredAt;

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
}
