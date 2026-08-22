package app.kitapla.service;

import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Tier kotası: üye 1/3, öğrenci 3/10. */
@SpringBootTest
@ActiveProfiles("test")
class QuotaServiceTest {

    @Autowired QuotaService quotaService;
    @Autowired UserRepository users;

    private User newUser(String email, boolean approvedStudent) {
        User u = new User();
        u.setName("Test");
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setAddress("Adres");
        if (approvedStudent) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    @Test
    void uyeKotasiHaftalikBirAylikUctur() {
        User uye = newUser("kota-uye@test.local", false);
        Quota q = quotaService.quotaFor(uye);
        assertThat(q.tier()).isEqualTo("member");
        assertThat(q.weeklyLimit()).isEqualTo(1);
        assertThat(q.monthlyLimit()).isEqualTo(3);
        assertThat(q.canReceive()).isTrue();
        assertThat(quotaService.cannotReceiveReason(uye)).isNull();
    }

    @Test
    void ogrenciKotasiHaftalikUcAylikOndur() {
        User ogrenci = newUser("kota-ogrenci@test.local", true);
        Quota q = quotaService.quotaFor(ogrenci);
        assertThat(q.tier()).isEqualTo("student");
        assertThat(q.weeklyLimit()).isEqualTo(3);
        assertThat(q.monthlyLimit()).isEqualTo(10);
        assertThat(q.weeklyRemaining()).isEqualTo(3);
    }

    @Test
    void bekleyenBelgeliKullaniciHenuzOgrenciSayilmaz() {
        User u = newUser("kota-bekleyen@test.local", false);
        u.setStudentStatus(StudentStatus.PENDING);
        users.save(u);
        assertThat(quotaService.quotaFor(u).tier()).isEqualTo("member");
    }
}
