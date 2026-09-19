package app.kitapla.service;

import app.kitapla.config.Features;
import app.kitapla.domain.School;
import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.domain.AuthToken;
import app.kitapla.domain.TokenType;
import app.kitapla.mail.MailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import app.kitapla.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Features features;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final app.kitapla.security.UserSessionService userSessions;
    private final TokenService tokens;
    private final MailService mail;
    private final NotificationService notifications;

    @PersistenceContext
    private EntityManager entityManager;

    public UserService(Features features, UserRepository users, PasswordEncoder encoder,
                       app.kitapla.security.UserSessionService userSessions,
                       TokenService tokens, MailService mail, NotificationService notifications) {
        this.notifications = notifications;
        this.features = features;
        this.users = users;
        this.encoder = encoder;
        this.userSessions = userSessions;
        this.tokens = tokens;
        this.mail = mail;
    }

    public static String normalizeEmail(String e) {
        return e == null ? null : e.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean isStudentEmail(String email) {
        String e = normalizeEmail(email);
        return e != null && e.length() <= 254
                && e.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+edu\\.tr");
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * Üye kaydı; belge alanları verilmişse öğrenci doğrulaması PENDING başlar.
     * documentPath dosya kaydedildikten sonra controller tarafından verilir (yoksa null).
     */
    @Transactional
    public User register(String name, String email, String rawPassword, String address, String phone,
                         School school,
                         boolean wantsStudent, SchoolLevel level, String documentNo, String documentPath) {
        name = clean(name, 120);
        email = normalizeEmail(email);
        address = clean(address, 500);
        phone = clean(phone, 40);
        documentNo = clean(documentNo, 100);

        if (name == null || email == null || rawPassword == null || rawPassword.isBlank())
            throw new IllegalArgumentException("Ad, e-posta ve şifre zorunludur.");
        if (!EMAIL.matcher(email).matches())
            throw new IllegalArgumentException("Geçerli bir e-posta adresi girin.");
        if (rawPassword.length() < 6)
            throw new IllegalArgumentException("Şifre en az 6 karakter olmalıdır.");
        if (users.existsByEmail(email))
            throw new IllegalArgumentException("Bu e-posta zaten kayıtlı.");
        if (isStudentEmail(email) && !okulAdresiniAyir(email, null))
            throw new IllegalArgumentException("Bu okul adresi başka bir hesapta öğrenci doğrulaması için kullanılıyor.");

        if (wantsStudent) {
            if (level == null) throw new IllegalArgumentException("Okul seviyesi seçilmelidir.");
            if (documentNo == null) throw new IllegalArgumentException("Öğrenci belge numarası zorunludur.");
            if (documentPath == null) throw new IllegalArgumentException("Öğrenci belgesi yüklenmelidir.");
            // Adres yalnızca kargo akışında gerekir (kampüs içi teslimde istenmez)
            if (features.isAddress() && address == null)
                throw new IllegalArgumentException("Öğrenci doğrulaması için teslimat adresi zorunludur.");
            if (users.existsByDocumentNo(documentNo))
                throw new IllegalArgumentException("Bu belge numarası ile zaten bir kayıt var.");
        }

        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setAddress(address);
        u.setPhone(phone);
        u.setSchool(school);

        if (isStudentEmail(email)) {
            u.setStudentEmail(email);
            u.setStudentStatus(StudentStatus.PENDING);
            u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        }

        if (wantsStudent) {
            u.setStudentStatus(StudentStatus.PENDING);
            u.setSchoolLevel(level);
            u.setDocumentNo(documentNo);
            u.setDocumentPath(documentPath);
        }
        users.save(u);
        if (u.getStudentEmail() != null) sendStudentVerification(u);
        return u;
    }

    // ---------- Profil ----------

    /**
     * Ad, adres, telefon ve okulu günceller. Adres {@code null} gelirse (kampüs modunda
     * web formu ve mobil istemci adres göndermez) kayıttaki adres korunur; boş metin siler.
     */
    @Transactional
    public User updateProfile(User user, String name, String address, String phone, School school) {
        User u = users.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));

        String n = clean(name, 120);
        if (n == null) throw new IllegalStateException("Ad boş olamaz.");
        u.setName(n);
        if (address != null) u.setAddress(clean(address, 500));
        u.setPhone(clean(phone, 40));
        if (school != null) u.setSchool(school);
        return users.save(u);
    }

    /** Mevcut şifre doğrulanarak yeni şifre atar. */
    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        User u = users.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));

        if (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash()))
            throw new IllegalStateException("Mevcut şifren hatalı.");
        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalStateException("Yeni şifre en az 6 karakter olmalı.");
        if (!newPassword.equals(confirmPassword))
            throw new IllegalStateException("Yeni şifreler birbiriyle eşleşmiyor.");
        if (encoder.matches(newPassword, u.getPasswordHash()))
            throw new IllegalStateException("Yeni şifre eskisiyle aynı olamaz.");

        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
        userSessions.expireUserSessions(u.getId());
    }

    @Transactional
    public User verifyStudentEmail(User user, String eduEmail) {
        User u = lockedUser(user);

        String e = normalizeEmail(eduEmail);
        if (e == null || e.isBlank() || !EMAIL.matcher(e).matches())
            throw new IllegalStateException("Geçerli bir e-posta adresi girin.");
        if (!isStudentEmail(e))
            throw new IllegalStateException(
                    "Adres .edu.tr ile bitmelidir. Okulunun verdiği e-posta adresini gir.");
        if (u.getStudentStatus() == StudentStatus.APPROVED)
            throw new IllegalStateException("Zaten onaylı bir öğrencisin.");
        if (!e.equals(u.getEmail()) && users.existsByEmail(e))
            throw new IllegalStateException("Bu adres başka bir hesabın giriş e-postası.");
        if (!e.equals(u.getStudentEmail()) && !okulAdresiniAyir(e, u.getId()))
            throw new IllegalStateException("Bu okul adresi başka bir hesapta kullanılıyor.");

        u.setStudentVerificationSent(false);
        boolean canSend = mail.isEnabled() && !tokens.limitDoldu(u, TokenType.EMAIL_VERIFY);
        if (!canSend && u.getStudentEmail() != null) return u;

        u.setStudentEmail(e);
        u.setStudentStatus(StudentStatus.PENDING);
        if (u.getSchoolLevel() == null) u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        users.save(u);
        sendStudentVerification(u);
        return u;
    }

    /**
     * Okul adresini bu hesap için ayırır. Adres başka bir hesapta yalnızca <b>doğrulanmamış</b>
     * duruyorsa o talep düşürülür: aksi hâlde herhangi biri başkasının okul adresini profiline
     * yazıp (bağlantıyı hiç onaylayamasa da) adresin gerçek sahibinin o adresle kayıt olmasını
     * ve doğrulama yapmasını engelleyebiliyordu. Doğrulanmış (onaylı) adres korunur.
     *
     * @param benimId adresi isteyen hesap; yeni kayıtta null
     * @return adres bu hesap için kullanılabilir mi
     */
    private boolean okulAdresiniAyir(String eduEmail, Long benimId) {
        User sahibi = users.findByStudentEmail(eduEmail).orElse(null);
        if (sahibi == null || sahibi.getId().equals(benimId)) return true;
        if (sahibi.getStudentStatus() == StudentStatus.APPROVED) return false;

        sahibi.setStudentEmail(null);
        if (sahibi.getStudentStatus() == StudentStatus.PENDING && sahibi.getDocumentPath() == null)
            sahibi.setStudentStatus(StudentStatus.NONE);
        users.saveAndFlush(sahibi);   // benzersizlik kısıtı yeni sahibin yazımından önce boşalsın
        tokens.invalidate(sahibi, TokenType.EMAIL_VERIFY);
        return true;
    }

    private User lockedUser(User user) {
        User u = entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (u == null || u.isBlocked()) throw new IllegalStateException("Kullanıcı bulunamadı.");
        return u;
    }

    private void sendStudentVerification(User user) {
        user.setStudentVerificationSent(false);
        if (!mail.isEnabled() || tokens.limitDoldu(user, TokenType.EMAIL_VERIFY)) return;
        String token = tokens.issue(user, TokenType.EMAIL_VERIFY);
        // Teslim arka planda yapılır; başarısız olursa bağlantı geçersiz kılınır ve üye site içinden
        // bilgilendirilir (istek anında "gönderiliyor" denmişti, sessiz kalmamalı).
        User alici = user;
        String okulAdresi = user.getStudentEmail();
        boolean sent = mail.send(okulAdresi, "Okul e-postanı doğrula", "ogrenci-dogrulama",
                Map.of("ad", user.getName(), "eposta", okulAdresi,
                        "link", mail.baseUrl() + "/profil/ogrenci/eposta/onay?token=" + token),
                () -> {
                    tokens.invalidate(alici, TokenType.EMAIL_VERIFY);
                    notifications.notify(alici, "hesap", "Okul e-postana (" + okulAdresi + ") doğrulama "
                            + "bağlantısı gönderilemedi. Profilindeki öğrenci doğrulama sayfasından tekrar deneyebilirsin.",
                            "/profil/ogrenci");
                });
        user.setStudentVerificationSent(sent);
        if (!sent) tokens.invalidate(user, TokenType.EMAIL_VERIFY);
    }

    public String studentVerificationMessage(User user) {
        return user.isStudentVerificationSent()
                ? "Doğrulama bağlantısı okul adresine gönderiliyor; birkaç dakika içinde gelmezse gereksiz (spam) klasörüne bak. Bağlantıyı onaylayana kadar öğrenci önceliğin yok."
                : "Öğrenci doğrulaman beklemede; e-posta gönderilmedi. Profilinden daha sonra tekrar dene. Normal üyeliğini kullanabilirsin.";
    }

    @Transactional
    public User confirmStudentEmail(User user, String token) {
        User u = lockedUser(user);
        if (!mail.isEnabled()) throw new IllegalStateException("E-posta doğrulaması şu an kullanılamıyor.");
        AuthToken proof = tokens.verify(token, TokenType.EMAIL_VERIFY)
                .filter(t -> t.getUser().getId().equals(u.getId()))
                .filter(t -> t.getVerificationEmail() != null && t.getVerificationEmail().equals(u.getStudentEmail()))
                .filter(t -> isStudentEmail(t.getVerificationEmail()))
                .orElseThrow(() -> new IllegalStateException("Bağlantı geçersiz ya da süresi dolmuş. Yeniden doğrulama iste."));
        if (u.getStudentStatus() != StudentStatus.PENDING)
            throw new IllegalStateException("Bekleyen e-posta doğrulaması bulunamadı.");
        tokens.consume(proof);
        u.setStudentStatus(StudentStatus.APPROVED);
        return users.save(u);
    }

    /**
     * Üyeyken öğrenci doğrulamasına başvurur; belge admin onayına gider.
     * Onaylı öğrenci ya da incelemedeki başvuru varsa tekrar başvurulamaz.
     */
    @Transactional
    public User applyForStudent(User user, SchoolLevel level, String documentNo, String documentPath) {
        User u = users.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));

        if (u.getStudentStatus() == StudentStatus.APPROVED)
            throw new IllegalStateException("Zaten onaylı bir öğrencisin.");
        if (u.getStudentStatus() == StudentStatus.PENDING && u.getDocumentPath() != null)
            throw new IllegalStateException("Belgen zaten incelemede.");
        if (level == null) throw new IllegalStateException("Okul seviyesi seçilmeli.");

        String docNo = clean(documentNo, 100);
        if (docNo == null) throw new IllegalStateException("Öğrenci belge numarası zorunlu.");
        if (documentPath == null) throw new IllegalStateException("Öğrenci belgesi yüklenmeli.");
        if (features.isAddress() && (u.getAddress() == null || u.getAddress().isBlank()))
            throw new IllegalStateException("Önce profilinden teslimat adresi eklemelisin.");

        if (users.existsByDocumentNo(docNo) && !docNo.equals(u.getDocumentNo()))
            throw new IllegalStateException("Bu belge numarası başka bir kayıtta kullanılıyor.");

        u.setStudentStatus(StudentStatus.PENDING);
        u.setSchoolLevel(level);
        u.setDocumentNo(docNo);
        u.setDocumentPath(documentPath);
        return users.save(u);
    }
}
