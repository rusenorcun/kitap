package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Uzun serbest metinler sütun sınırında kırpılır; işlem veritabanı hatasıyla düşmez. */
@SpringBootTest
@ActiveProfiles("test")
class MetinSiniriTest {

    @Autowired DonationService donationService;
    @Autowired SwapService swapService;
    @Autowired RequestService requestService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag) {
        User u = new User();
        u.setName("Metin " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        return users.save(u);
    }

    private Book book() {
        Book b = new Book();
        b.setTitle("Metin Kitabı " + UUID.randomUUID());
        return books.save(b);
    }

    @Test
    void uzunAciklamaliBagisTakasaAktarilabilir() {
        User u = mk("aktar");
        Donation d = donationService.create(u, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, "a".repeat(450));

        SwapBook sb = donationService.moveToSwap(d.getId(), u, null);

        assertThat(sb.getNote()).hasSize(300);
    }

    @Test
    void sinirdanUzunMetinlerKirpilir() {
        User u = mk("kirp");
        User diger = mk("kirp-diger");

        assertThat(donationService.create(u, book(), 1, TargetLevel.HEPSI, DonationSource.OWN, "b".repeat(900))
                .getDescription()).hasSize(500);
        assertThat(requestService.create(u, book(), "c".repeat(900)).getDescription()).hasSize(500);

        SwapBook benim = swapService.open(u, book(), "d".repeat(900));
        assertThat(benim.getNote()).hasSize(300);
        SwapBook onun = swapService.open(diger, book(), null);
        assertThat(swapService.offer(onun.getId(), benim.getId(), u, "e".repeat(900)).getMessage()).hasSize(300);
    }
}
