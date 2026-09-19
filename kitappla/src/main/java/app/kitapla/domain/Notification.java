package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "ix_notif_user_read", columnList = "user_id, readFlag"),
        @Index(name = "ix_notif_user_created", columnList = "user_id, createdAt")})
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 255)
    private String link;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean readFlag = false;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /** Şablonlarda gösterim için yerel saat biçimi (Thymeleaf Instant'ı doğrudan biçimleyemiyor). */
    @Transient
    public String getCreatedAtText() {
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(createdAt);
    }

    /**
     * Bildirimin yönlendireceği hedef bağlantı.
     * Varsa açıkça atanmış bağlantıyı döner; yoksa bildirim türüne göre mantıklı varsayılanı üretir.
     */
    @Transient
    public String getResolvedLink() {
        if (link != null && !link.isBlank()) {
            return link.trim();
        }
        if (type == null) {
            return "/panom";
        }
        return switch (type) {
            case "mesaj" -> "/mesajlar";
            case "swap_offer", "swap_accepted", "swap_shipped", "swap_completed", "swap_rejected" -> "/takas/takaslarim";
            case "donation_claimed", "claim_delivered", "thank_you", "claim_cancelled" -> "/bagislarim";
            case "claim_shipped" -> "/aldiklarim";
            case "request_fulfilled", "request_shipped" -> "/isteklerim";
            case "request_delivered" -> "/karsiladiklarim";
            case "belge", "hesap" -> "/profil";
            case "sikayet" -> "/admin/sikayetler";
            case "sikayet_sonuc" -> "/sikayetlerim";
            case "bulusma_hatirlatma" -> "/panom";
            default -> "/panom";
        };
    }
}
