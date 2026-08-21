package app.kitapla.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private Instant createdAt = Instant.now();
}
