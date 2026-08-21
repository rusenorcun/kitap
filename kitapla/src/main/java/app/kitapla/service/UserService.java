package app.kitapla.service;

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

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public static String normalizeEmail(String e) {
        return e == null ? null : e.trim().toLowerCase();
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

        if (wantsStudent) {
            if (level == null) throw new IllegalArgumentException("Okul seviyesi seçilmelidir.");
            if (documentNo == null) throw new IllegalArgumentException("Öğrenci belge numarası zorunludur.");
            if (documentPath == null) throw new IllegalArgumentException("Öğrenci belgesi yüklenmelidir.");
            if (address == null) throw new IllegalArgumentException("Öğrenci doğrulaması için teslimat adresi zorunludur.");
            if (users.existsByDocumentNo(documentNo))
                throw new IllegalArgumentException("Bu belge numarası ile zaten bir kayıt var.");
        }

        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setAddress(address);
        u.setPhone(phone);
        if (wantsStudent) {
            u.setStudentStatus(StudentStatus.PENDING);
            u.setSchoolLevel(level);
            u.setDocumentNo(documentNo);
            u.setDocumentPath(documentPath);
        }
        return users.save(u);
    }
}
