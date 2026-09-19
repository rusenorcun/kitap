package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * Kampüs içinde yüz yüze teslimin yapılacağı nokta.
 * <p>
 * Noktalar yönetim tarafından tanımlanır; üyeler listeden seçer. Listede olmayan
 * bir yer için serbest metin de girilebilir (bkz. ilgili kayıtlardaki
 * {@code meetingNote} alanları).
 */
@Entity
@Table(name = "pickup_points")
@Getter
@Setter
public class PickupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kampüs ya da yerleşke adı — "Tınaztepe", "Merkez Kampüs" gibi. */
    @Column(nullable = false, length = 120)
    private String campus;

    /** Noktanın adı — "Merkez Kütüphane girişi" gibi. */
    @Column(nullable = false, length = 160)
    private String name;

    /** Tarif: "Turnikelerin solundaki bank" gibi. */
    @Column(length = 400)
    private String description;

    /** Pasif noktalar yeni seçimlerde görünmez; geçmiş kayıtlar bozulmaz. */
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /** Listelerde ve bildirimlerde kullanılan tam ad. */
    @Transient
    public String getFullName() {
        return campus + " — " + name;
    }
}
