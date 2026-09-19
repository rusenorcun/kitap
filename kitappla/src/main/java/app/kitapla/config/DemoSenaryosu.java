package app.kitapla.config;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import app.kitapla.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Denemelik tam senaryo: her akışın her durumundan en az bir örnek.
 * <p>
 * {@link DataSeeder} yalnızca açılış için gerekeni kurar (yönetici, teslim
 * noktaları, katalog). Web ve mobil istemcileri elle denerken bunlar yetmiyor:
 * teslim alınmış bir bağış, kabul edilmiş bir takas, yürüyen bir sohbet ve
 * açık bir şikâyet olmadan ekranların çoğu boş görünüyor. Bu sınıf o durumları
 * üretir.
 * <p>
 * Kayıtlar doğrudan veritabanına yazılmaz, <b>servisler üzerinden</b> kurulur:
 * böylece kota, yetki ve durum geçişi kuralları demo veride de geçerli olur ve
 * tutarsız bir kayıt oluşamaz. Yalnızca geçmişe tarihleme (buluşma saatinin
 * geçmesi gibi) SQL ile yapılır, çünkü servisler geçmiş zamanı kabul etmez.
 * <p>
 * {@code kitapla.seed.demo=false} ise hiç çalışmaz; bir kez kurulduktan sonra
 * da tekrar çalışmaz (talep varsa çıkar).
 */
@Component
@Order(2)
public class DemoSenaryosu implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSenaryosu.class);
    private static final String SIFRE = "sifre123";

    private final UserRepository users;
    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;
    private final PickupPointRepository points;
    private final BookService bookService;
    private final DonationService donationService;
    private final RequestService requestService;
    private final SwapService swapService;
    private final MessageService messages;
    private final ReportService reports;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${kitapla.seed.demo:true}") private boolean demoVerisi;

    public DemoSenaryosu(UserRepository users, ClaimRepository claims, BookRequestRepository requests,
                         SwapOfferRepository offers, PickupPointRepository points,
                         BookService bookService, DonationService donationService,
                         RequestService requestService, SwapService swapService,
                         MessageService messages, ReportService reports,
                         PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users;
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
        this.points = points;
        this.bookService = bookService;
        this.donationService = donationService;
        this.requestService = requestService;
        this.swapService = swapService;
        this.messages = messages;
        this.reports = reports;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (!demoVerisi) return;
        // Senaryo bir kez kurulur; uygulama her açılışta yeniden üretmez.
        if (claims.count() > 0 || requests.count() > 0) return;

        try {
            kur();
        } catch (RuntimeException ex) {
            // Demo verisi uygulamanın açılmasını engellememeli.
            log.warn("Demo senaryosu kurulamadı: {}", ex.getMessage());
        }
    }

    private void kur() {
        User admin = users.findFirstByAdminTrueOrderByIdAsc().orElse(null);
        User ayse = users.findByEmail("ayse@ornek.com").orElse(null);
        User elif = users.findByEmail("elif@ornek.com").orElse(null);
        if (ayse == null || elif == null) {
            log.info("Temel örnek üyeler yok; demo senaryosu atlandı.");
            return;
        }

        User can = uye("Can Öztürk", "can@ornek.com", SchoolLevel.UNIVERSITE, "UNI-3312");
        User burak = uye("Burak Şahin", "burak@ornek.com", SchoolLevel.ORTAOKUL, "OO-1190");
        User zeynep = uye("Zeynep Arslan", "zeynep@ornek.com", null, null);

        Long nokta = points.findAll().stream().findFirst().map(PickupPoint::getId).orElse(null);

        // ---------- Bağışlar: ikinci bir bağışçı ----------
        bagis(zeynep, "Nutuk", "Mustafa Kemal Atatürk", TargetLevel.HEPSI, 2);
        bagis(zeynep, "Tutunamayanlar", "Oğuz Atay", TargetLevel.UNIVERSITE, 1);
        bagis(zeynep, "Şeker Portakalı", "José Mauro de Vasconcelos", TargetLevel.ORTAOKUL, 3);

        // ---------- Bağış teslimatı: her durumdan bir örnek ----------
        // (a) Yeni talep — henüz buluşma yok
        Claim bekleyen = talep("Dönüşüm", elif);

        // (b) Buluşma ayarlanmış
        Claim ayarli = talep("Kürk Mantolu Madonna", can);
        if (ayarli != null) {
            donationService.arrange(ayarli.getId(), can, bulusma(nokta, "Kütüphane girişi", 2));
            sohbet(ConversationKind.CLAIM, ayarli.getId(), can,
                    "Merhaba, salı 14:00 uygun mu?",
                    ayse, "Uygun, kütüphane girişinde olurum.");
        }

        // (c) Teslim edilmiş — teslim sonrası şikâyet denenebilsin
        Claim teslim = talep("Yabancı", burak);
        if (teslim != null) {
            donationService.arrange(teslim.getId(), ayse, bulusma(nokta, "Kantin girişi", 1));
            donationService.deliver(teslim.getId(), burak);
            donationService.thank(teslim.getId(), burak, "Çok teşekkürler, kitap harika durumda.");
        }

        // ---------- İstekler ----------
        istek(elif, "Geometri 10", "MEB Yayınları", "Konu anlatımlı olursa çok iyi olur.");
        BookRequest acik = istek(can, "Sapiens", "Yuval Noah Harari", "İkinci el olabilir.");

        // Karşılanmış ama buluşma ayarlanmamış
        BookRequest karsilanan = istek(burak, "Fen Bilimleri 7", "MEB Yayınları", null);
        if (karsilanan != null) {
            requestService.fulfill(karsilanan.getId(), zeynep, DonationSource.OWN);
        }

        // Karşılanmış + buluşma ayarlanmış + sohbet açık
        BookRequest bulusmali = istek(elif, "Osmanlı Tarihi", "Halil İnalcık", "Cildi yıpranmış olabilir.");
        if (bulusmali != null) {
            requestService.fulfill(bulusmali.getId(), ayse, DonationSource.OWN);
            requestService.arrange(bulusmali.getId(), ayse, bulusma(nokta, "Mühendislik lobisi", 3));
            sohbet(ConversationKind.REQUEST, bulusmali.getId(), elif,
                    "Kitabı ayırdığınız için teşekkürler!",
                    ayse, "Rica ederim, perşembe görüşürüz.");
        }

        // Teslim edilmiş istek
        BookRequest biten = istek(can, "Nutuk (cep boy)", "Mustafa Kemal Atatürk", null);
        if (biten != null) {
            requestService.fulfill(biten.getId(), zeynep, DonationSource.OWN);
            requestService.arrange(biten.getId(), zeynep, bulusma(nokta, "Yemekhane önü", 1));
            requestService.deliver(biten.getId(), can);
        }

        // ---------- Takas ----------
        SwapBook elifKitabi = takasKitabi(elif, "Körlük", "José Saramago", "Distopya olsun");
        SwapBook canKitabi = takasKitabi(can, "Fahrenheit 451", "Ray Bradbury", "Klasik arıyorum");
        SwapBook zeynepKitabi = takasKitabi(zeynep, "Bülbülü Öldürmek", "Harper Lee", "Polisiye olur");
        SwapBook zeynepKitabi2 = takasKitabi(zeynep, "Martı", "Richard Bach", null);
        SwapBook burakKitabi = takasKitabi(burak, "Küçük Prens", "Antoine de Saint-Exupéry", null);

        // (a) Yanıt bekleyen teklif + sohbet
        SwapOffer bekleyenTeklif = teklif(canKitabi, elifKitabi, can, "Merhaba, bu takas ilgini çeker mi?");
        if (bekleyenTeklif != null) {
            sohbet(ConversationKind.SWAP, bekleyenTeklif.getId(), can,
                    "Kitabın durumu nasıl?",
                    elif, "Kapağı hafif yıpranmış ama sayfalar temiz.");
        }

        // (b) Kabul edilmiş + buluşma ayarlanmış
        SwapOffer kabulEdilen = teklif(zeynepKitabi, burakKitabi, zeynep, "Değişelim mi?");
        if (kabulEdilen != null) {
            swapService.accept(kabulEdilen.getId(), burak);
            swapService.arrange(kabulEdilen.getId(), burak, bulusma(nokta, "Spor salonu girişi", 4));
        }

        // (c) Tamamlanmış takas — takas sonrası şikâyet denenebilsin
        SwapBook aySeKitabi = takasKitabi(ayse, "Satranç", "Stefan Zweig", null);
        SwapOffer tamamlanan = teklif(zeynepKitabi2, aySeKitabi, zeynep, "Martı'ya karşılık Satranç?");
        if (tamamlanan != null) {
            swapService.accept(tamamlanan.getId(), ayse);
            swapService.arrange(tamamlanan.getId(), ayse, bulusma(nokta, "Kütüphane girişi", 1));
            swapService.ship(tamamlanan.getId(), zeynep);
            swapService.ship(tamamlanan.getId(), ayse);
        }

        // ---------- Şikâyetler ----------
        // (a) Açık şikâyet — yönetim kuyruğunda bekler
        if (elifKitabi != null) {
            sikayet(burak, ReportKind.SWAP_BOOK, elifKitabi.getId(), ReportReason.SAHTE,
                    "İlandaki kitap açıklamayla uyuşmuyor.");
        }

        // (b) Açık şikâyet + yönetim destek sohbeti
        if (teslim != null) {
            Report r = sikayet(burak, ReportKind.CLAIM, teslim.getId(), ReportReason.HASARLI,
                    "Kitabın son yaprakları eksik çıktı.");
            if (r != null && admin != null) {
                sohbet(ConversationKind.REPORT, r.getId(), burak,
                        "Fotoğraf gönderebilir miyim?",
                        admin, "Tabii, buradan iletebilirsin. İnceliyoruz.");
            }
        }

        // (c) Sonuçlandırılmış şikâyet — kullanıcı tarafında "işlem yapıldı" görünür
        if (bekleyenTeklif != null && admin != null) {
            Report r = sikayet(elif, ReportKind.SWAP_OFFER, bekleyenTeklif.getId(),
                    ReportReason.SPAM, "Tekrar tekrar aynı teklifi gönderiyor.");
            if (r != null) {
                reports.resolve(r.getId(), admin, true, "Üyeye uyarı gönderildi.");
            }
        }

        // ---------- Geçmişe tarihleme ----------
        // Buluşma saati geçmiş bir kayıt olsun ki "Gelmedi" düğmesi denenebilsin.
        // Servisler geçmiş zamanı reddettiği için bu adım SQL ile yapılır.
        if (ayarli != null) {
            jdbc.update("UPDATE claims SET meeting_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), ayarli.getId());
        }

        log.info("Demo senaryosu hazır: {} teslimat, {} istek, {} takas teklifi.",
                claims.count(), requests.count(), offers.count());
        log.info("Deneme hesapları (şifre {}): ayse@ornek.com, elif@ornek.com, can@ornek.com, "
                + "burak@ornek.com, zeynep@ornek.com, mert@ornek.com", SIFRE);
        if (bekleyen == null || acik == null || burakKitabi == null) {
            log.debug("Senaryonun bazı parçaları kurulamadı; kalanlar yine de kullanılabilir.");
        }
    }

    // ---------- Yardımcılar ----------

    /** Öğrenci seviyesi null ise onaysız üye olarak açılır. */
    private User uye(String ad, String eposta, SchoolLevel seviye, String belgeNo) {
        return users.findByEmail(eposta).orElseGet(() -> {
            User u = new User();
            u.setName(ad);
            u.setEmail(eposta);
            u.setPasswordHash(encoder.encode(SIFRE));
            if (seviye != null) {
                u.setStudentStatus(StudentStatus.APPROVED);
                u.setSchoolLevel(seviye);
                u.setDocumentNo(belgeNo);
            }
            return users.save(u);
        });
    }

    private void bagis(User bagisci, String baslik, String yazar, TargetLevel seviye, int adet) {
        Book b = bookService.findOrCreate(baslik, yazar, null, null, null, bagisci.getId());
        donationService.create(bagisci, b, adet, seviye, DonationSource.OWN, "Temiz durumda.");
    }

    /** Başlığa göre açık bir bağıştan talep oluşturur; uygun bağış yoksa null. */
    private Claim talep(String kitapBasligi, User alici) {
        return donationService.openDonations(DonationService.Filter.none()).stream()
                .filter(v -> kitapBasligi.equals(v.donation().getBook().getTitle()))
                .filter(v -> donationService.eligibility(v, alici).allowed())
                .findFirst()
                .map(v -> donationService.claim(v.getId(), alici))
                .orElse(null);
    }

    private BookRequest istek(User isteyen, String baslik, String yazar, String not) {
        Book b = bookService.findOrCreate(baslik, yazar, null, null, null, isteyen.getId());
        try {
            return requestService.create(isteyen, b, not);
        } catch (RuntimeException ex) {
            return null;   // kota dolduysa sessizce atla
        }
    }

    private SwapBook takasKitabi(User sahibi, String baslik, String yazar, String not) {
        Book b = bookService.findOrCreate(baslik, yazar, null, null, null, sahibi.getId());
        try {
            return swapService.open(sahibi, b, not);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private SwapOffer teklif(SwapBook verilen, SwapBook istenen, User teklifEden, String mesaj) {
        if (verilen == null || istenen == null) return null;
        try {
            return swapService.offer(istenen.getId(), verilen.getId(), teklifEden, mesaj);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** İki mesajlık kısa bir sohbet açar (ilk mesaj bir taraftan, yanıt diğerinden). */
    private void sohbet(ConversationKind tur, Long refId, User soran, String soru,
                        User yanitlayan, String yanit) {
        try {
            Conversation c = messages.open(tur, refId, soran);
            messages.send(c.getId(), soran, soru);
            messages.send(c.getId(), yanitlayan, yanit);
        } catch (RuntimeException ex) {
            log.debug("Demo sohbeti açılamadı ({} #{}): {}", tur, refId, ex.getMessage());
        }
    }

    private Report sikayet(User eden, ReportKind tur, Long refId, ReportReason gerekce, String not) {
        try {
            return reports.create(eden, tur, refId, gerekce, not);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Bugünden {@code gunSonra} gün sonrası için buluşma isteği. */
    private MeetingRequest bulusma(Long noktaId, String not, int gunSonra) {
        return new MeetingRequest(noktaId, not, Instant.now().plus(gunSonra, ChronoUnit.DAYS));
    }
}
