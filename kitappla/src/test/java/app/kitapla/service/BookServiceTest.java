package app.kitapla.service;

import app.kitapla.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kitap veri tabanı: find-or-create tekilliği. */
@SpringBootTest
@ActiveProfiles("test")
class BookServiceTest {

    @Autowired BookService bookService;

    @Test
    void ayniAdVeYazarIkinciKezOlusturulmaz() {
        Book first = bookService.findOrCreate("Yabancı", "Camus", null, null, null, null);
        Book again = bookService.findOrCreate("yabancı", "camus", null, null, null, null);
        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void farkliYazarAyriKitaptir() {
        Book a = bookService.findOrCreate("Dönüşüm", "Kafka", null, null, null, null);
        Book b = bookService.findOrCreate("Dönüşüm", "Başka Yazar", null, null, null, null);
        assertThat(b.getId()).isNotEqualTo(a.getId());
    }

    @Test
    void baslikYoksaVeLinkCalismazsaHataVerir() {
        assertThatThrownBy(() -> bookService.findOrCreate(null, "Yazar", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kitap adı");
    }

    @Test
    void aramaBasligaGoreBulur() {
        bookService.findOrCreate("Beyaz Diş", "Jack London", null, null, null, null);
        assertThat(bookService.search("beyaz")).extracting(Book::getTitle).contains("Beyaz Diş");
    }

    @Test
    void kapakRengiBaslikIcinSabittir() {
        Book b1 = bookService.findOrCreate("Sabit Renk", "Y", null, null, null, null);
        String c1 = b1.getCoverColor();
        assertThat(c1).startsWith("#");
        assertThat(b1.getCoverColor()).isEqualTo(c1);
        assertThat(b1.hasCover()).isFalse();
    }
}
