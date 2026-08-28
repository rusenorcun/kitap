package app.kitapla.config;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final BookRepository books;
    private final DonationRepository donations;
    private final PickupPointRepository pickupPoints;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${kitapla.admin.email}") private String adminEmail;
    @Value("${kitapla.admin.password}") private String adminPassword;
    @Value("${kitapla.admin.name}") private String adminName;
    @Value("${kitapla.upload-dir}") private String uploadDir;

    public DataSeeder(UserRepository users, BookRepository books, DonationRepository donations,
                      PickupPointRepository pickupPoints, PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.pickupPoints = pickupPoints;
        this.users = users;
        this.books = books;
        this.donations = donations;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Override
    public void run(String... args) {
        if ("admin123".equals(adminPassword)) {
            log.warn("Yönetici hesabı varsayılan şifreyle çalışıyor ({}). Yerel deneme dışında "
                    + "KITAPLA_ADMIN_PASSWORD ortam değişkenini ayarla.", adminEmail);
        }

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

        // 2) Teslim noktaları (yalnızca hiç yoksa) — kampüs içi yüz yüze teslim için
        if (pickupPoints.count() == 0) {
            record Nokta(String kampus, String ad, String tarif) {}
            List.of(
                    new Nokta("Merkez Kampüs", "Merkez Kütüphane girişi", "Turnikelerin solundaki banklar"),
                    new Nokta("Merkez Kampüs", "Öğrenci Merkezi kantini", "Kantin girişindeki masa"),
                    new Nokta("Merkez Kampüs", "Mühendislik Fakültesi lobisi", "Asansörlerin karşısı"),
                    new Nokta("Tınaztepe Kampüsü", "Yemekhane önü", "Ana giriş kapısı"),
                    new Nokta("Tınaztepe Kampüsü", "Spor salonu girişi", null)
            ).forEach(n -> {
                PickupPoint p = new PickupPoint();
                p.setCampus(n.kampus());
                p.setName(n.ad());
                p.setDescription(n.tarif());
                pickupPoints.save(p);
            });
        }

        // 3) Örnek veri (yalnızca boşsa)
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

        // Yönetim panelinde inceleyecek bir başvuru olsun diye bekleyen bir belge
        User bekleyen = new User();
        bekleyen.setName("Mert Kaya");
        bekleyen.setEmail("mert@ornek.com");
        bekleyen.setPasswordHash(encoder.encode("sifre123"));
        bekleyen.setStudentStatus(StudentStatus.PENDING);
        bekleyen.setSchoolLevel(SchoolLevel.UNIVERSITE);
        bekleyen.setDocumentNo("UNI-5507");
        bekleyen.setAddress("Tınaztepe Kampüsü, Buca / İzmir");
        bekleyen.setDocumentPath(seedDocument());
        users.save(bekleyen);

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

    /**
     * Bekleyen başvuru için örnek bir belge dosyası yazar; yönetim panelindeki
     * "Belgeyi aç" bağlantısı yerelde de çalışsın diye.
     */
    private String seedDocument() {
        try {
            Path dir = Path.of(uploadDir, "documents");
            Files.createDirectories(dir);
            String fileName = "ornek-ogrenci-belgesi.txt";
            Files.writeString(dir.resolve(fileName),
                    "ÖRNEK ÖĞRENCİ BELGESİ\n\nAd Soyad: Mert Kaya\nBelge No: UNI-5507\n"
                            + "Okul: Örnek Üniversitesi\n\n(Bu dosya yalnızca yerel deneme verisidir.)\n");
            return fileName;
        } catch (Exception ex) {
            return null; // belge yazılamazsa başvuru belgesiz görünür, akış bozulmaz
        }
    }
}
