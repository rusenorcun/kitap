package app.kitapla.config;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final BookRepository books;
    private final DonationRepository donations;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${kitapla.admin.email}") private String adminEmail;
    @Value("${kitapla.admin.password}") private String adminPassword;
    @Value("${kitapla.admin.name}") private String adminName;

    public DataSeeder(UserRepository users, BookRepository books, DonationRepository donations,
                      PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users;
        this.books = books;
        this.donations = donations;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 1) Admin garanti
        users.findByEmail(adminEmail).ifPresentOrElse(u -> {
            if (!u.isAdmin()) { u.setAdmin(true); users.save(u); }
        }, () -> {
            User a = new User();
            a.setName(adminName);
            a.setEmail(adminEmail);
            a.setPasswordHash(encoder.encode(adminPassword));
            a.setAdmin(true);
            users.save(a);
        });

        // 2) Örnek veri (yalnızca boşsa)
        if (books.count() > 0) return;

        User donor = new User();
        donor.setName("Ayşe Demir");
        donor.setEmail("ayse@ornek.com");
        donor.setPasswordHash(encoder.encode("sifre123"));
        donor.setAddress("İzmir");
        donor = users.save(donor);

        User student = new User();
        student.setName("Elif Yılmaz");
        student.setEmail("elif@ornek.com");
        student.setPasswordHash(encoder.encode("sifre123"));
        student.setStudentStatus(StudentStatus.APPROVED);
        student.setSchoolLevel(SchoolLevel.LISE);
        student.setDocumentNo("LS-2841");
        student.setAddress("Kazımdirik Mah., Bornova / İzmir");
        users.save(student);

        record Seed(String title, String author, TargetLevel level, int qty) {}
        List<Seed> seeds = List.of(
                new Seed("Suç ve Ceza", "Dostoyevski", TargetLevel.UNIVERSITE, 2),
                new Seed("Sefiller", "Victor Hugo", TargetLevel.LISE, 1),
                new Seed("Matematik 8", "MEB Yayınları", TargetLevel.ORTAOKUL, 3),
                new Seed("Simyacı", "Paulo Coelho", TargetLevel.LISE, 1),
                new Seed("1984", "George Orwell", TargetLevel.UNIVERSITE, 1),
                new Seed("Beyaz Diş", "Jack London", TargetLevel.ORTAOKUL, 2),
                new Seed("Hayvan Çiftliği", "George Orwell", TargetLevel.LISE, 1),
                new Seed("Fizik 11", "MEB Yayınları", TargetLevel.LISE, 2),
                new Seed("Kürk Mantolu Madonna", "Sabahattin Ali", TargetLevel.HEPSI, 2),
                new Seed("Yabancı", "Albert Camus", TargetLevel.HEPSI, 1),
                new Seed("Dönüşüm", "Franz Kafka", TargetLevel.HEPSI, 3)
        );
        for (Seed s : seeds) {
            Book b = new Book();
            b.setTitle(s.title());
            b.setAuthor(s.author());
            b.setCreatedBy(donor.getId());
            b = books.save(b);

            Donation d = new Donation();
            d.setDonor(donor);
            d.setBook(b);
            d.setQuantity(s.qty());
            d.setTargetLevel(s.level());
            d.setSource(DonationSource.PURCHASE);
            donations.save(d);
        }

        // Örnek verinin bir kısmında öncelik penceresi dolmuş olsun ki
        // üye akışı da yerelde denenebilsin (createdAt updatable=false, bu yüzden SQL).
        jdbc.update("UPDATE donations SET created_at = ? WHERE id IN "
                        + "(SELECT id FROM donations ORDER BY id DESC LIMIT 3)",
                Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)));
    }
}
