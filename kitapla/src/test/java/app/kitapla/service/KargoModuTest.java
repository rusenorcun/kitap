package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kargo ve adres akışı kampüs teslimine geçilirken silinmedi, yalnızca kapatıldı.
 * Bu test bayraklar yeniden açıldığında eski davranışın hâlâ çalıştığını doğrular;
 * "ileride açarız" sözünün karşılığı budur.
 */
@SpringBootTest(properties = {
        "kitapla.features.address=true",
        "kitapla.features.shipping=true",
        "kitapla.features.purchase=true"
})
@ActiveProfiles("test")
class KargoModuTest {

    @Autowired DonationService donationService;
    @Autowired SwapService swapService;
    @Autowired RequestService requestService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired app.kitapla.repo.ClaimRepository claims;
    @Autowired PasswordEncoder encoder;
    @Autowired app.kitapla.config.Features features;

    private User mk(String tag, String address, boolean student) {
        User u = new User();
        u.setName("Kargo " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        if (student) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private Book book(String title) {
        Book b = new Book();
        b.setTitle(title + " " + UUID.randomUUID());
        return books.save(b);
    }

    @Test
    void bayraklarAcikOkunur() {
        assertThat(features.isAddress()).isTrue();
        assertThat(features.isShipping()).isTrue();
        assertThat(features.isPurchase()).isTrue();
    }

    @Test
    void adresZorunlulugoGeriGelir() {
        User adressiz = mk("adressiz", null, false);

        assertThatThrownBy(() -> donationService.create(adressiz, book("K"), 1,
                TargetLevel.HEPSI, DonationSource.PURCHASE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adres");

        assertThatThrownBy(() -> swapService.open(adressiz, book("T"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adres");

        assertThatThrownBy(() -> requestService.create(adressiz, book("I"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adres");
    }

    @Test
    void adressizAliciBagisAlamaz() {
        User donor = mk("bagisci", "Bir Adres", false);
        User adressiz = mk("alici", null, true);
        Donation d = donationService.create(donor, book("Kitap"), 1,
                TargetLevel.HEPSI, DonationSource.PURCHASE, null);

        var uygunluk = donationService.eligibility(donationService.view(d.getId()).orElseThrow(), adressiz);
        assertThat(uygunluk.allowed()).isFalse();
        assertThat(uygunluk.code()).isEqualTo("ADDRESS_REQUIRED");
    }

    @Test
    void kargoAkisiUctanUcaCalisir() {
        User donor = mk("kargocu", "Gonderen Adres", false);
        User alici = mk("alan", "Alan Adres", true);
        Donation d = donationService.create(donor, book("Kargolu"), 1,
                TargetLevel.HEPSI, DonationSource.PURCHASE, null);

        Claim c = donationService.claim(d.getId(), alici);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.MATCHED);

        donationService.ship(c.getId(), donor);
        assertThat(claimStatus(c)).isEqualTo(ClaimStatus.SHIPPED);

        donationService.deliver(c.getId(), alici);
        assertThat(claimStatus(c)).isEqualTo(ClaimStatus.DELIVERED);
    }

    private ClaimStatus claimStatus(Claim c) {
        return claims.findByIdWithDetails(c.getId()).orElseThrow().getStatus();
    }
}
