package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.NotificationRepository;
import app.kitapla.repo.SwapBookRepository;
import app.kitapla.repo.SwapOfferRepository;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Takas: kitabı aç, teklif ver, kabul/ret, çift kargo ile tamamlama. */
@SpringBootTest
@ActiveProfiles("test")
class SwapServiceTest {

    @Autowired SwapService swapService;
    @Autowired BookService bookService;
    @Autowired UserRepository users;
    @Autowired SwapBookRepository swapBooks;
    @Autowired SwapOfferRepository offers;
    @Autowired NotificationRepository notifications;

    private User user(String tag, String address) {
        User u = new User();
        u.setName("Takas " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress(address);
        return users.save(u);
    }

    private Book book(String prefix) {
        return bookService.findOrCreate(prefix + " " + UUID.randomUUID(), "Yazar", null, null, null, null);
    }

    private SwapBook openBook(User u, String prefix) {
        return swapService.open(u, book(prefix), "Klasik isterim");
    }

    @Test
    void kitapTakasaAcilirVeBaskalarininKesfindeGorunur() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapBook s = openBook(ali, "Açık Kitap");

        assertThat(s.getStatus()).isEqualTo(SwapBookStatus.OPEN);
        assertThat(swapService.discover(veli, null)).extracting(SwapBook::getId).contains(s.getId());
        // Kendi kitabı keşifte görünmez
        assertThat(swapService.discover(ali, null)).extracting(SwapBook::getId).doesNotContain(s.getId());
    }

    @Test
    void adressizKullaniciTakasaAcamaz() {
        User adressiz = user("adressiz", null);
        assertThatThrownBy(() -> swapService.open(adressiz, book("X"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adres");
    }

    @Test
    void ayniKitapIkinciKezTakasaAcilamaz() {
        User ali = user("ali", "İzmir");
        Book b = book("Tekil");
        swapService.open(ali, b, null);
        assertThatThrownBy(() -> swapService.open(ali, b, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten takasa açtın");
    }

    @Test
    void teklifVerilirVeHedefSahibineBildirimGider() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapBook aliKitap = openBook(ali, "Ali");
        SwapBook veliKitap = openBook(veli, "Veli");

        SwapOffer o = swapService.offer(aliKitap.getId(), veliKitap.getId(), veli, "Olur mu?");

        assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(o.getFromUser().getId()).isEqualTo(veli.getId());
        assertThat(o.getToUser().getId()).isEqualTo(ali.getId());
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(ali))
                .extracting(Notification::getType).contains("swap_offer");
        assertThat(swapService.incoming(ali)).hasSize(1);
        assertThat(swapService.outgoing(veli)).hasSize(1);
    }

    @Test
    void kendiKitabinaTeklifVerilemez() {
        User ali = user("ali", "İzmir");
        SwapBook a1 = openBook(ali, "A1");
        SwapBook a2 = openBook(ali, "A2");
        assertThatThrownBy(() -> swapService.offer(a1.getId(), a2.getId(), ali, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kendi kitabına");
    }

    @Test
    void baskasininKitabiTeklifEdilemez() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        User can = user("can", "Bursa");
        SwapBook aliKitap = openBook(ali, "Ali");
        SwapBook canKitap = openBook(can, "Can");

        assertThatThrownBy(() -> swapService.offer(aliKitap.getId(), canKitap.getId(), veli, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kendi kitabını");
    }

    @Test
    void ayniHedefeMukerrerBekleyenTeklifVerilemez() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapBook hedef = openBook(ali, "Hedef");
        SwapBook v1 = openBook(veli, "V1");

        swapService.offer(hedef.getId(), v1.getId(), veli, null);
        SwapBook v2 = openBook(veli, "V2");
        assertThatThrownBy(() -> swapService.offer(hedef.getId(), v2.getId(), veli, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bekleyen bir teklifin");
    }

    @Test
    void kabulIkiKitabiKapatirVeAdresleriAcar() {
        User ali = user("ali", "İzmir Adres");
        User veli = user("veli", "Ankara Adres");
        SwapBook aliKitap = openBook(ali, "Ali");
        SwapBook veliKitap = openBook(veli, "Veli");
        SwapOffer o = swapService.offer(aliKitap.getId(), veliKitap.getId(), veli, null);

        assertThat(swapService.addressVisible(o)).isFalse();   // kabulden önce gizli

        SwapOffer accepted = swapService.accept(o.getId(), ali);

        assertThat(accepted.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(swapService.addressVisible(accepted)).isTrue();
        assertThat(swapBooks.findById(aliKitap.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);
        assertThat(swapBooks.findById(veliKitap.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(veli))
                .extracting(Notification::getType).contains("swap_accepted");
    }

    @Test
    void kabulRakipTeklifleriReddeder() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        User can = user("can", "Bursa");
        SwapBook hedef = openBook(ali, "Hedef");
        SwapBook veliKitap = openBook(veli, "Veli");
        SwapBook canKitap = openBook(can, "Can");

        SwapOffer veliTeklif = swapService.offer(hedef.getId(), veliKitap.getId(), veli, null);
        SwapOffer canTeklif = swapService.offer(hedef.getId(), canKitap.getId(), can, null);

        swapService.accept(veliTeklif.getId(), ali);

        assertThat(offers.findById(canTeklif.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(can))
                .extracting(Notification::getType).contains("swap_rejected");
    }

    @Test
    void ciftKargoTakasiTamamlar() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapOffer o = swapService.offer(openBook(ali, "Ali").getId(), openBook(veli, "Veli").getId(), veli, null);
        swapService.accept(o.getId(), ali);

        swapService.ship(o.getId(), ali);
        assertThat(offers.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.ACCEPTED);

        swapService.ship(o.getId(), veli);
        SwapOffer done = offers.findById(o.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OfferStatus.COMPLETED);
        assertThat(done.getFromShippedAt()).isNotNull();
        assertThat(done.getToShippedAt()).isNotNull();
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(ali))
                .extracting(Notification::getType).contains("swap_completed");
    }

    @Test
    void ayniTarafIkiKezKargolayamaz() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapOffer o = swapService.offer(openBook(ali, "Ali").getId(), openBook(veli, "Veli").getId(), veli, null);
        swapService.accept(o.getId(), ali);
        swapService.ship(o.getId(), ali);

        assertThatThrownBy(() -> swapService.ship(o.getId(), ali))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zaten kargoya verdin");
    }

    @Test
    void kabulEdilmemisTakasKargolanamaz() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapOffer o = swapService.offer(openBook(ali, "Ali").getId(), openBook(veli, "Veli").getId(), veli, null);

        assertThatThrownBy(() -> swapService.ship(o.getId(), ali))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kabul edilmiş");
    }

    @Test
    void reddetVeGeriCekCalisir() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");

        SwapOffer red = swapService.offer(openBook(ali, "A").getId(), openBook(veli, "V").getId(), veli, null);
        swapService.reject(red.getId(), ali);
        assertThat(offers.findById(red.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(veli))
                .extracting(Notification::getType).contains("swap_rejected");

        SwapOffer geri = swapService.offer(openBook(ali, "A2").getId(), openBook(veli, "V2").getId(), veli, null);
        swapService.cancel(geri.getId(), veli);
        assertThat(offers.findById(geri.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
    }

    @Test
    void baskasininTeklifineMudahaleEdilemez() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        User yabanci = user("yabanci", "Bursa");
        SwapOffer o = swapService.offer(openBook(ali, "A").getId(), openBook(veli, "V").getId(), veli, null);

        assertThatThrownBy(() -> swapService.accept(o.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> swapService.reject(o.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> swapService.cancel(o.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        // teklif eden kabul edemez
        assertThatThrownBy(() -> swapService.accept(o.getId(), veli)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bekleyenTeklifliKitapKaldirilamaz() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        SwapBook hedef = openBook(ali, "Hedef");
        swapService.offer(hedef.getId(), openBook(veli, "V").getId(), veli, null);

        assertThatThrownBy(() -> swapService.removeBook(hedef.getId(), ali))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kaldırılamaz");
    }

    @Test
    void teklifsizKitapKaldirilirVeGizlenebilir() {
        User ali = user("ali", "İzmir");
        SwapBook s = openBook(ali, "Serbest");

        swapService.setStatus(s.getId(), ali, SwapBookStatus.CLOSED);
        assertThat(swapBooks.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SwapBookStatus.CLOSED);

        swapService.removeBook(s.getId(), ali);
        assertThat(swapBooks.findById(s.getId())).isEmpty();
    }

    @Test
    void aramaBasligaGoreSuzer() {
        User ali = user("ali", "İzmir");
        User veli = user("veli", "Ankara");
        Book b = bookService.findOrCreate("Takas Arama Kitabı", "Özel", null, null, null, null);
        swapService.open(ali, b, null);

        assertThat(swapService.discover(veli, "Takas Arama")).isNotEmpty();
        assertThat(swapService.discover(veli, "boyle-kitap-yok")).isEmpty();
    }
}
