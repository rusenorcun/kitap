package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean admin = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    private StudentStatus studentStatus = StudentStatus.NONE;

    @Enumerated(EnumType.STRING)
    private SchoolLevel schoolLevel;

    @Column(unique = true)
    private String documentNo;

    private String documentPath;

    @Column(length = 500)
    private String address;

    private String phone;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean blocked = false;

    /** Buluşmaya gelmediği bildirilen sefer sayısı; yönetim üye listesinde görür. */
    @Column(nullable = false)
    @ColumnDefault("0")
    private int noShowCount = 0;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    /** Öğrenci = belgesi onaylanmış üye */
    @Transient
    public boolean isStudent() {
        return studentStatus == StudentStatus.APPROVED;
    }

    @Transient
    public String getRecipientTier() {
        return isStudent() ? "student" : "member";
    }

    @Transient
    public String getInitials() {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }
}
