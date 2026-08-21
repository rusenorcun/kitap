package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "requests")
@Getter
@Setter
public class BookRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private DonationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    private User fulfilledBy;

    private Instant fulfilledAt;
    private Instant shippedAt;
    private Instant deliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
