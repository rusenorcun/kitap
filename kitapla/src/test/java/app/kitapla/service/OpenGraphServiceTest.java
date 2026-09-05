package app.kitapla.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OpenGraph ayrıştırıcısı — ağ erişimi olmadan, sabit HTML ile. */
class OpenGraphServiceTest {

    private final OpenGraphService og = new OpenGraphService();

    @Test
    void ogTitleVeImageCikarir() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Simyacı"/>
                  <meta property="og:image" content="https://ornek.com/kapak.jpg"/>
                  <meta property="og:description" content="Bir yolculuk hikayesi"/>
                  <title>Yedek Başlık</title>
                </head><body></body></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.title()).isEqualTo("Simyacı");
        assertThat(m.imageUrl()).isEqualTo("https://ornek.com/kapak.jpg");
        assertThat(m.description()).isEqualTo("Bir yolculuk hikayesi");
    }

    @Test
    void ogYoksaTitleEtiketiniKullanir() {
        BookMetadata m = og.parse("<html><head><title>Sadece Başlık</title></head><body></body></html>");
        assertThat(m.title()).isEqualTo("Sadece Başlık");
        assertThat(m.imageUrl()).isNull();
    }

    @Test
    void yazarBilinenMetaEtiketlerindenOkunur() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Suç ve Ceza"/>
                  <meta name="author" content="Dostoyevski"/>
                </head></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.author()).isEqualTo("Dostoyevski");
    }

    @Test
    void bosVeyaGecersizGirdiBosSonucDondurur() {
        assertThat(og.parse(null).isEmpty()).isTrue();
        assertThat(og.parse("").isEmpty()).isTrue();
        // Ağ gerektiren çağrı: geçersiz şema doğrudan boş döner
        assertThat(og.fetch("ftp://ornek.com/kitap").isEmpty()).isTrue();
        assertThat(og.fetch(null).isEmpty()).isTrue();
    }

    @Test
    void htmlEntityleriCozulur() {
        BookMetadata m = og.parse("<html><head><meta property=\"og:title\" content=\"Sefiller &amp; Devamı\"/></head></html>");
        assertThat(m.title()).isEqualTo("Sefiller & Devamı");
    }

    @Test
    void ssrfAdresleriFetchIleEngellenirVeBosDoner() {
        assertThat(og.fetch("http://localhost:8080/h2").isEmpty()).isTrue();
        assertThat(og.fetch("http://127.0.0.1:8080").isEmpty()).isTrue();
        assertThat(og.fetch("http://169.254.169.254/latest/meta-data/").isEmpty()).isTrue();
        assertThat(og.fetch("http://10.0.0.1/admin").isEmpty()).isTrue();
        assertThat(og.fetch("http://192.168.1.1").isEmpty()).isTrue();
        assertThat(og.fetch("http://[::1]:8080").isEmpty()).isTrue();
        assertThat(og.fetch("file:///etc/passwd").isEmpty()).isTrue();
    }
}
