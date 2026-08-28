package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** İstek akışı: oluştur, karşıla, kargola, teslim al, teşekkür, kaldır. */
@SpringBootTest
@ActiveProfiles("test")
class RequestServiceTest {

    @Autowired RequestService requestService;
    @Autowired BookService bookService;
    @Autowired UserRepository users;
    @Autowired BookRequestRepository requests;
    @Autowired NotificationRepository notifications;

    private User user(String tag, boolean student, String address) {
        User u = new User();
        u.setName("İstek " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress(address);
        if (student) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private Book book() {
        return bookService.findOrCreate("İstek Kitabı " + UUID.randomUUID(), "Yazar", null, null, null, null);
    }

    @Test
    void istekOlusturulurVeAcikListedeGorunur() {
        User isteyen = user("isteyen", true, "Ankara");
        BookRequest r = requestService.create(isteyen, book(), "Ödevim için lazım");

        assertThat(r.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(requestService.openRequests(null)).extracting(BookRequest::getId).contains(r.getId());
        assertThat(requestService.myRequests(isteyen)).hasSize(1);
    }

    @Test
    void kampusTeslimindeAdressizIstekOlusturulabilir() {
        // Yüz yüze teslimde adres gerekmez; kargo modunda yeniden istenir (KargoModuTest)
        User adressiz = user("adressiz", true, null);
        assertThat(requestService.create(adressiz, book(), null)).isNotNull();
    }

    @Test
    void ayniKitapIcinIkinciAcikIstekOlusturulamaz() {
        User isteyen = user("mukerrer", true, "Ankara");
        Book b = book();
        requestService.create(isteyen, b, null);
        assertThatThrownBy(() -> requestService.create(isteyen, b, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten açık bir isteğin");
    }

    @Test
    void acikIstekSayisiSinirlidir() {
        User isteyen = user("limit", true, "Ankara");
        for (int i = 0; i < RequestService.MAX_OPEN_REQUESTS; i++) {
            requestService.create(isteyen, book(), null);
        }
        assertThatThrownBy(() -> requestService.create(isteyen, book(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en fazla");
    }

    @Test
    void istekKarsilanirVeIsteyeneBildirimGider() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        BookRequest r = requestService.create(isteyen, book(), null);

        requestService.fulfill(r.getId(), karsilayan, DonationSource.PURCHASE);

        BookRequest updated = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.FULFILLED);
        assertThat(updated.getFulfilledBy().getId()).isEqualTo(karsilayan.getId());
        assertThat(updated.getFulfilledAt()).isNotNull();
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(isteyen))
                .extracting(Notification::getType).contains("request_fulfilled");
        assertThat(requestService.fulfilledByMe(karsilayan)).hasSize(1);
    }

    @Test
    void kendiIsteginiKarsilayamaz() {
        User isteyen = user("kendi", true, "Ankara");
        BookRequest r = requestService.create(isteyen, book(), null);
        assertThatThrownBy(() -> requestService.fulfill(r.getId(), isteyen, DonationSource.PURCHASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kendi isteğini");
    }

    @Test
    void ayniIstekIkiKezKarsilanamaz() {
        User isteyen = user("isteyen", true, "Ankara");
        User a = user("a", false, "İzmir");
        User b = user("b", false, "Bursa");
        BookRequest r = requestService.create(isteyen, book(), null);

        requestService.fulfill(r.getId(), a, DonationSource.OWN);
        assertThatThrownBy(() -> requestService.fulfill(r.getId(), b, DonationSource.OWN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten karşılanmış");
    }

    @Test
    void isteyeninKotasiDoluysaKarsilanamaz() {
        User uye = user("kotali", false, "Ankara");        // üye: haftada 1
        User karsilayan = user("karsilayan", false, "İzmir");

        BookRequest ilk = requestService.create(uye, book(), null);
        requestService.fulfill(ilk.getId(), karsilayan, DonationSource.PURCHASE);   // kota doldu

        BookRequest ikinci = requestService.create(uye, book(), null);
        assertThatThrownBy(() -> requestService.fulfill(ikinci.getId(), karsilayan, DonationSource.PURCHASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kotası dolu");
    }

    @Test
    void teslimatAkisiKargolaTeslimTesekkur() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        requestService.ship(r.getId(), karsilayan);
        assertThat(requests.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.SHIPPED);
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(isteyen))
                .extracting(Notification::getType).contains("request_shipped");

        requestService.deliver(r.getId(), isteyen);
        assertThat(requests.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.DELIVERED);

        requestService.thank(r.getId(), isteyen, "Sağ ol!");
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(karsilayan))
                .extracting(Notification::getType).contains("request_delivered", "thank_you");
    }

    @Test
    void baskasininIstegineMudahaleEdilemez() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        User yabanci = user("yabanci", false, "Bursa");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        assertThatThrownBy(() -> requestService.ship(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> requestService.deliver(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> requestService.delete(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acikIstekKaldirilirKarsilanmisKaldirilamaz() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");

        BookRequest acik = requestService.create(isteyen, book(), null);
        requestService.delete(acik.getId(), isteyen);
        assertThat(requests.findById(acik.getId())).isEmpty();

        BookRequest dolu = requestService.create(isteyen, book(), null);
        requestService.fulfill(dolu.getId(), karsilayan, DonationSource.OWN);
        assertThatThrownBy(() -> requestService.delete(dolu.getId(), isteyen))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kaldırılamaz");
    }

    @Test
    void karsilananIstekKotadanDuser() {
        User uye = user("kota", false, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");

        assertThat(requestService.openRequests(null)).isNotNull();
        BookRequest r = requestService.create(uye, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.PURCHASE);

        // Üye haftalık kotası 1 -> dolmuş olmalı
        assertThat(requestService.openRequests(null)).isNotNull();
        assertThat(requests.countByStudentAndStatusInAndFulfilledAtAfter(uye,
                java.util.List.of(RequestStatus.FULFILLED, RequestStatus.SHIPPED, RequestStatus.DELIVERED),
                java.time.Instant.now().minusSeconds(3600))).isEqualTo(1);
    }

    @Test
    void aramaBasligaGoreSuzer() {
        User isteyen = user("arama", true, "Ankara");
        Book b = bookService.findOrCreate("Arama Testi Kitabı", "Özel Yazar", null, null, null, null);
        requestService.create(isteyen, b, null);

        assertThat(requestService.openRequests("Arama Testi")).isNotEmpty();
        assertThat(requestService.openRequests("boyle-bir-kitap-yok")).isEmpty();
    }
}
