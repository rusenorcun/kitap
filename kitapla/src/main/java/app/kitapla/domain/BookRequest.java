package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

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
    @ColumnDefault("'OPEN'")
    private RequestStatus status = RequestStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private DonationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    private User fulfilledBy;

    private Instant fulfilledAt;
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
