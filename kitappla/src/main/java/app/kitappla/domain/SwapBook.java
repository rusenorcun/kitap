package app.kitappla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "swap_books", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}),
       indexes = @Index(name = "ix_swap_books_status", columnList = "status, createdAt"))
@Getter
@Setter
public class SwapBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'OPEN'")
    private SwapBookStatus status = SwapBookStatus.OPEN;

    /**
     * Yönetim ilanı yayından kaldırdı. Sahibi (ya da gelinmedi/iptal akışı) ilanı
     * yeniden açamaz; aksi hâlde moderasyon tek tıkla geri alınabilirdi.
     */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean removedByAdmin = false;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();
}
