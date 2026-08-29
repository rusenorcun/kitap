package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(length = 1000)
    private String coverUrl;

    @Column(length = 1000)
    private String purchaseLink;

    @Column(length = 1000)
    private String description;

    private Long createdBy;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /** Kapak görseli yoksa, başlıktan türetilen sabit bir yer tutucu rengi. */
    @Transient
    public String getCoverColor() {
        String[] palette = {"#7A2E2A", "#2F4A3C", "#23405C", "#7A2E5A", "#4A5240", "#8a5a1c", "#5A3A6B", "#2E5E5A"};
        String key = title == null ? "" : title;
        int h = 0;
        for (int i = 0; i < key.length(); i++) h = 31 * h + key.charAt(i);
        return palette[Math.floorMod(h, palette.length)];
    }

    @Transient
    public boolean hasCover() {
        return coverUrl != null && !coverUrl.isBlank();
    }
}
