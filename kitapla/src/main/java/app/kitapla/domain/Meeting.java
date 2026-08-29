package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Kampüs içi yüz yüze buluşma bilgisi. Bağış talebi, karşılanan istek ve
 * kabul edilen takas kayıtlarına gömülür.
 * <p>
 * Nokta ya yönetimin tanımladığı listeden seçilir ({@code point}) ya da listede
 * yoksa serbest metin olarak yazılır ({@code note}). İkisi birden dolu olabilir:
 * seçilen noktaya ek açıklama ("kütüphane girişinde, saat 14'te kırmızı çanta").
 */
@Embeddable
@Getter
@Setter
public class Meeting {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_point_id")
    private PickupPoint point;

    /** Listede olmayan yer ya da ek tarif. */
    @Column(name = "meeting_note", length = 300)
    private String note;

    /** Kararlaştırılan buluşma zamanı. */
    @Column(name = "meeting_at")
    private Instant at;

    /** Buluşmanın kaydedildiği an; boşsa henüz ayarlanmamış demektir. */
    @Column(name = "meeting_arranged_at")
    private Instant arrangedAt;

    /** Hatırlatma gönderildiği an; aynı buluşma için iki kez hatırlatılmaz. */
    @Column(name = "meeting_reminded_at")
    private Instant remindedAt;

    @Transient
    public boolean isArranged() {
        return arrangedAt != null;
    }

    /** Şablonlarda gösterim: seçilen nokta ve/veya serbest metin. */
    @Transient
    public String getPlaceText() {
        if (point != null && note != null && !note.isBlank()) return point.getFullName() + " · " + note;
        if (point != null) return point.getFullName();
        return note;
    }

    /** Şablonlarda gösterim için yerel saat (Thymeleaf Instant'ı biçimleyemiyor). */
    @Transient
    public String getAtText() {
        if (at == null) return null;
        return DateTimeFormatter.ofPattern("d MMMM EEEE, HH:mm", new java.util.Locale("tr"))
                .withZone(ZoneId.systemDefault()).format(at);
    }
}
