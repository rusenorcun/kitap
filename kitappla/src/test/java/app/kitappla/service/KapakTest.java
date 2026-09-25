package app.kitappla.service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import app.kitappla.domain.Book;
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
    @Value("${kitappla.upload-dir}") String uploadDir;

    /** Bildirilen türle uyumlu, gerçekten okunabilir küçük bir görsel. */
    private MockMultipartFile gorsel(String type) throws Exception {
        String bicim = type.endsWith("png") ? "png" : "jpg";
        BufferedImage resim = new BufferedImage(10, 10,
                bicim.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream cikti = new ByteArrayOutputStream();
        ImageIO.write(resim, bicim, cikti);
        return new MockMultipartFile("coverFile", "kapak." + bicim, type, cikti.toByteArray());
    }

    private MockMultipartFile gercekJpeg(int genislik, int yukseklik) throws Exception {
        BufferedImage resim = new BufferedImage(genislik, yukseklik, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream cikti = new ByteArrayOutputStream();
        ImageIO.write(resim, "jpg", cikti);
        return new MockMultipartFile("coverFile", "kapak.jpg", "image/jpeg", cikti.toByteArray());
    }

    private Path dosyasi(String adres) {
        return Path.of(uploadDir, "covers", adres.substring("/uploads/covers/".length()));
    }

    @Test
    void genisKapakKaydedilirkenOraniKorunarakKucultulur() throws Exception {
        BufferedImage kayitli = ImageIO.read(dosyasi(covers.saveUpload(gercekJpeg(1600, 2400))).toFile());
        assertThat(kayitli.getWidth()).isEqualTo(CoverService.EN_GENIS_PIKSEL);
        assertThat(kayitli.getHeight()).isEqualTo(900);
    }

    @Test
    void darKapakOlduguGibiKalir() throws Exception {
        BufferedImage kayitli = ImageIO.read(dosyasi(covers.saveUpload(gercekJpeg(300, 450))).toFile());
        assertThat(kayitli.getWidth()).isEqualTo(300);
    }

    @Test
    void yuklenenGorselKaydedilirVeKendiAdresimizdenSunulur() throws Exception {
        String adres = covers.saveUpload(gorsel("image/jpeg"));

        assertThat(adres).startsWith("/uploads/covers/").endsWith(".jpg");
        Path dosya = Path.of(uploadDir, "covers", adres.substring("/uploads/covers/".length()));
        assertThat(Files.exists(dosya)).isTrue();
    }

    @Test
    void gorselTuruBildirilipBaskaIcerikGonderilirseReddedilir() {
        // Content-Type istemcinin kendi seçtiği bir değer: baytlar doğrulanmazsa
        // /uploads/covers/ altına ".png" adıyla rastgele içerik konulabiliyordu.
        assertThatThrownBy(() -> covers.saveUpload(
                new MockMultipartFile("coverFile", "kapak.png", "image/png", "<html>merhaba</html>".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPG");
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
    void icAgAdresiNeIndirilirNeDeOlduguGibiBirakilir() {
        // Ulaşılamayan bir genel adres olduğu gibi bırakılır (tarayıcı kendi dener),
        // ama iç ağ / loopback adresi SSRF denetiminden geçemez: ne indirilir ne de
        // kapak olarak saklanır. Aksi halde uygulama iç ağını yoklayan bir araç olurdu.
        assertThat(covers.saveFromUrl("http://localhost:1/olmayan.jpg")).isNull();
        assertThat(covers.saveFromUrl("http://127.0.0.1/olmayan.jpg")).isNull();
        assertThat(covers.saveFromUrl("http://192.168.1.5/olmayan.jpg")).isNull();
        assertThat(covers.saveFromUrl("http://169.254.169.254/latest/meta-data/")).isNull();
    }

    @Test
    void yuklenenGorsel_bagisFormundakiAdresinOnuneGecer() throws Exception {
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
