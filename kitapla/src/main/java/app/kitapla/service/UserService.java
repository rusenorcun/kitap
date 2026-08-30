package app.kitapla.service;

import app.kitapla.config.Features;
import app.kitapla.domain.School;
import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
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

    public UserService(Features features, UserRepository users, PasswordEncoder encoder) {
        this.features = features;
        this.users = users;
        this.encoder = encoder;
    }

    public static String normalizeEmail(String e) {
        return e == null ? null : e.trim().toLowerCase();
    }

    /**
     * Okul e-postası mı? Türkiye'de üniversite adresleri {@code .edu.tr} ile biter;
     * öğrenci doğrulaması şimdilik yalnızca buna bakar.
     */
    public static boolean isStudentEmail(String email) {
        String e = normalizeEmail(email);
        return e != null && e.endsWith(".edu.tr");
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
        if (isStudentEmail(email) && users.existsByStudentEmail(email))
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

        // Okul e-postasıyla kaydolan doğrudan öğrenci sayılır; belge istenmez.
        if (isStudentEmail(email)) {
            u.setStudentEmail(email);
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        }

        if (wantsStudent) {
            u.setStudentStatus(StudentStatus.PENDING);
            u.setSchoolLevel(level);
            u.setDocumentNo(documentNo);
            u.setDocumentPath(documentPath);
        }
        return users.save(u);
    }

    // ---------- Profil ----------

    /** Ad, adres ve telefon günceller. Boş bırakılan alanlar değişmez. */
    @Transactional
    public User updateProfile(User user, String name, String address, String phone, School school) {
        User u = users.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));

        String n = clean(name, 120);
        if (n == null) throw new IllegalStateException("Ad boş olamaz.");
        u.setName(n);
        u.setAddress(clean(address, 500));
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
    }

    /**
     * Okul e-postasıyla öğrenci doğrulaması. Adres {@code .edu.tr} ile bitiyorsa üye
     * anında öğrenci olur.
     *
     * <p><b>Not:</b> Posta servisi bağlanana kadar adresin gerçekten üyeye ait olduğu
     * doğrulanmaz — yalnızca uzantıya bakılır. Servis geldiğinde bu adrese kod
     * gönderilip onay beklenecek; o yüzden adres burada ayrıca saklanıyor.</p>
     */
    @Transactional
    public User verifyStudentEmail(User user, String eduEmail) {
        User u = users.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));

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
        if (!e.equals(u.getStudentEmail()) && users.existsByStudentEmail(e))
            throw new IllegalStateException("Bu okul adresi başka bir hesapta kullanılıyor.");

        u.setStudentEmail(e);
        u.setStudentStatus(StudentStatus.APPROVED);
        if (u.getSchoolLevel() == null) u.setSchoolLevel(SchoolLevel.UNIVERSITE);
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
        if (u.getStudentStatus() == StudentStatus.PENDING)
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
