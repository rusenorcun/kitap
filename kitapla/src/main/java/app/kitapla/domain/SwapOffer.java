package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private OfferStatus status = OfferStatus.PENDING;

    private Instant fromShippedAt;
    private Instant toShippedAt;
    private Instant decidedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
