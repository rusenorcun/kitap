package app.kitapla.service;

import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Profil güncelleme, şifre değiştirme ve öğrenci doğrulama başvurusu. */
@SpringBootTest
@ActiveProfiles("test")
class ProfileServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Profil " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    @Test
    void profilGuncellenir() {
        User u = mk("guncelle", "Eski Adres");
        userService.updateProfile(u, "Yeni Ad", "Yeni Adres 42", "05551112233");

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Yeni Ad");
        assertThat(saved.getAddress()).isEqualTo("Yeni Adres 42");
        assertThat(saved.getPhone()).isEqualTo("05551112233");
    }

    @Test
    void bosAdReddedilir() {
        User u = mk("bosad", "Adres");
        assertThatThrownBy(() -> userService.updateProfile(u, "   ", "Adres", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ad boş");
    }

    @Test
    void sifreDegistirilirVeYeniSifreCalisir() {
        User u = mk("sifre", "Adres");
        userService.changePassword(u, "sifre123", "yenisifre1", "yenisifre1");

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(encoder.matches("yenisifre1", saved.getPasswordHash())).isTrue();
        assertThat(encoder.matches("sifre123", saved.getPasswordHash())).isFalse();
    }

    @Test
    void yanlisMevcutSifreReddedilir() {
        User u = mk("yanlis", "Adres");
        assertThatThrownBy(() -> userService.changePassword(u, "hatali", "yenisifre1", "yenisifre1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mevcut şifren hatalı");
    }

    @Test
    void esitsizVeKisaSifreReddedilir() {
        User u = mk("esitsiz", "Adres");
        assertThatThrownBy(() -> userService.changePassword(u, "sifre123", "yenisifre1", "baskasifre"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eşleşmiyor");
        assertThatThrownBy(() -> userService.changePassword(u, "sifre123", "123", "123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en az 6");
        assertThatThrownBy(() -> userService.changePassword(u, "sifre123", "sifre123", "sifre123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eskisiyle aynı");
    }

    @Test
    void ogrenciBasvurusuBeklemeyeAlinir() {
        User u = mk("basvuru", "Adres");
        userService.applyForStudent(u, SchoolLevel.LISE, "LS-" + UUID.randomUUID(), "belge.pdf");

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(saved.getSchoolLevel()).isEqualTo(SchoolLevel.LISE);
        assertThat(saved.isStudent()).isFalse();   // onaya kadar öğrenci değil
    }

    @Test
    void kampusTeslimindeAdressizOgrenciBasvurusuYapilabilir() {
        // Yüz yüze teslimde adres gerekmez; kargo modunda yeniden istenir (KargoModuTest)
        User u = mk("adressiz", null);
        User sonuc = userService.applyForStudent(u, SchoolLevel.LISE,
                "LS-" + java.util.UUID.randomUUID(), "belge.pdf");
        assertThat(sonuc.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
    }

    @Test
    void belgesizVeSeviyesizBasvuruReddedilir() {
        User u = mk("eksik", "Adres");
        assertThatThrownBy(() -> userService.applyForStudent(u, null, "LS-1", "belge.pdf"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> userService.applyForStudent(u, SchoolLevel.LISE, "LS-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yüklenmeli");
    }

    @Test
    void incelemedekiVeOnayliKullaniciTekrarBasvuramaz() {
        User bekleyen = mk("bekleyen", "Adres");
        userService.applyForStudent(bekleyen, SchoolLevel.LISE, "LS-" + UUID.randomUUID(), "b.pdf");
        assertThatThrownBy(() -> userService.applyForStudent(bekleyen, SchoolLevel.LISE, "LS-X", "b.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incelemede");

        User onayli = mk("onayli", "Adres");
        onayli.setStudentStatus(StudentStatus.APPROVED);
        users.save(onayli);
        assertThatThrownBy(() -> userService.applyForStudent(onayli, SchoolLevel.LISE, "LS-Y", "b.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("onaylı");
    }

    @Test
    void baskasininBelgeNumarasiKullanilamaz() {
        String docNo = "LS-ORTAK-" + UUID.randomUUID();
        User ilk = mk("ilk", "Adres");
        userService.applyForStudent(ilk, SchoolLevel.LISE, docNo, "b.pdf");

        User ikinci = mk("ikinci", "Adres");
        assertThatThrownBy(() -> userService.applyForStudent(ikinci, SchoolLevel.LISE, docNo, "b.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("başka bir kayıtta");
    }
}
