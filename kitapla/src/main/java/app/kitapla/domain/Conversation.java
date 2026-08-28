package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * İki üye arasında, belirli bir alışveriş üzerinden yürüyen sohbet.
 * <p>
 * Serbest mesajlaşma yoktur: sohbet ancak bir bağış talebi, karşılanan istek ya
 * da kabul edilmiş takas varsa açılır ve yalnızca o alışverişin iki tarafı
 * yazışabilir. Böylece taciz yüzeyi dar kalır ve moderasyon kolaylaşır.
 */
@Entity
@Table(name = "conversations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "ref_id"}),
       indexes = {@Index(name = "ix_conv_a", columnList = "user_a_id"),
                  @Index(name = "ix_conv_b", columnList = "user_b_id")})
@Getter
@Setter
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationKind kind;

    /** Bağlı olduğu Claim / BookRequest / SwapOffer kimliği. */
    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id")
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id")
    private User userB;

    /** Listeyi sıralamak ve önizleme göstermek için. */
    @Column(length = 200)
    private String lastMessage;

    private Instant lastMessageAt;

    /** Okunmamış sayısı bu iki damgaya göre hesaplanır. */
    private Instant lastReadA;
    private Instant lastReadB;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public boolean has(User u) {
        return u != null && (userA.getId().equals(u.getId()) || userB.getId().equals(u.getId()));
    }

    @Transient
    public User other(User me) {
        return userA.getId().equals(me.getId()) ? userB : userA;
    }

    @Transient
    public Instant lastReadOf(User me) {
        return userA.getId().equals(me.getId()) ? lastReadA : lastReadB;
    }

    public void markRead(User me, Instant at) {
        if (userA.getId().equals(me.getId())) lastReadA = at;
        else lastReadB = at;
    }

    /** Şablonlarda gösterim (Thymeleaf Instant'ı biçimleyemiyor). */
    @Transient
    public String getLastMessageAtText() {
        if (lastMessageAt == null) return null;
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(lastMessageAt);
    }
}
