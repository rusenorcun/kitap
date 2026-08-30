package app.kitapla.service;

import app.kitapla.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kapak görseli: yükleme, uzak adresi kendimizde saklama ve eksik kapağı tamamlama. */
@SpringBootTest
@ActiveProfiles("test")
class KapakTest {

    @Autowired CoverService covers;
    @Autowired BookService books;
    @Value("${kitapla.upload-dir}") String uploadDir;

    private MockMultipartFile gorsel(String type) {
        return new MockMultipartFile("coverFile", "kapak.jpg", type, "sahte-gorsel".getBytes());
    }

    @Test
    void yuklenenGorselKaydedilirVeKendiAdresimizdenSunulur() throws Exception {
        String adres = covers.saveUpload(gorsel("image/jpeg"));

        assertThat(adres).startsWith("/uploads/covers/").endsWith(".jpg");
        Path dosya = Path.of(uploadDir, "covers", adres.substring("/uploads/covers/".length()));
        assertThat(Files.exists(dosya)).isTrue();
    }

    @Test
    void gorselOlmayanDosyaReddedilir() {
        assertThatThrownBy(() -> covers.saveUpload(
                new MockMultipartFile("coverFile", "belge.pdf", "application/pdf", "x".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPG");
    }

    @Test
    void bosDosyaVeBosAdresSessizceGecilir() {
        assertThat(covers.saveUpload(null)).isNull();
        assertThat(covers.saveFromUrl(null)).isNull();
        assertThat(covers.saveFromUrl("   ")).isNull();
        assertThat(covers.saveFromUrl("ftp://ornek.com/a.jpg")).isNull();
    }

    @Test
    void kendiAdresimizTekrarIndirilmez() {
        assertThat(covers.saveFromUrl("/uploads/covers/abc.jpg")).isEqualTo("/uploads/covers/abc.jpg");
    }

    @Test
    void indirilemeyenAdresOlduguGibiKalir() {
        // Kapak hiç olmamasındansa tarayıcının doğrudan denemesi yeğdir
        String adres = "http://localhost:1/olmayan.jpg";
        assertThat(covers.saveFromUrl(adres)).isEqualTo(adres);
    }

    @Test
    void yuklenenGorsel_bagisFormundakiAdresinOnuneGecer() {
        String adres = covers.resolve(gorsel("image/png"), "http://localhost:1/uzak.jpg");
        assertThat(adres).startsWith("/uploads/covers/").endsWith(".png");
    }

    @Test
    void ayniKitapKapaksizEklendiyseSonradanGelenKapakDoldurulur() {
        String ad = "Kapaksız Kitap " + UUID.randomUUID();

        Book once = books.findOrCreate(ad, "Yazar", null, null, null, null);
        assertThat(once.getCoverUrl()).isNull();

        Book sonra = books.findOrCreate(ad, "Yazar", null, "/uploads/covers/x.jpg", null, null);

        assertThat(sonra.getId()).isEqualTo(once.getId());
        assertThat(sonra.getCoverUrl()).isEqualTo("/uploads/covers/x.jpg");
        assertThat(sonra.hasCover()).isTrue();
    }
}
