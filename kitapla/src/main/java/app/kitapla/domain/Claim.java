package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private ClaimStatus status = ClaimStatus.MATCHED;

    private Instant shippedAt;
    private Instant deliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
