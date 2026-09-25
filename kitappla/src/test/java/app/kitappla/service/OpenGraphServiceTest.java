package app.kitappla.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OpenGraph ve kitap sayfası ayrıştırıcısı testleri. */
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
    void jsonLdVerisindenYazarKapakVeAciklamaAyristirilir() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "Book",
                    "name": "Simyacı",
                    "image": "https://img.kitapyurdu.com/v1/getImage/fn:195086/wi:800/wh:adb8b61d7",
                    "description": "Santiago'nun masalsı yolculuğu...",
                    "author": {
                      "@type": "Person",
                      "name": "Paulo Coelho"
                    }
                  }
                  </script>
                </head><body></body></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.title()).isEqualTo("Simyacı");
        assertThat(m.author()).isEqualTo("Paulo Coelho");
        assertThat(m.imageUrl()).isEqualTo("https://img.kitapyurdu.com/v1/getImage/fn:195086/wi:800/wh:adb8b61d7");
        assertThat(m.description()).isEqualTo("Santiago'nun masalsı yolculuğu...");
    }

    @Test
    void basliktanYazarVeKitapAdiAyristirilir() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Simyacı - Paulo Coelho | Can Yayınları - D&R"/>
                </head><body></body></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.title()).isEqualTo("Simyacı");
        assertThat(m.author()).isEqualTo("Paulo Coelho");
    }

    @Test
    void parantezIciYazarAyristirilirVeSiteEkleriTemizlenir() {
        String html = """
                <html><head>
                  <title>Vahşi Savaşçının Mutsuzluğu (Pierre Clastres) Fiyatı, Yorumları, Satın Al - Kitapyurdu.com</title>
                </head><body></body></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.title()).isEqualTo("Vahşi Savaşçının Mutsuzluğu");
        assertThat(m.author()).isEqualTo("Pierre Clastres");
    }

    @Test
    void drBenzeriOgImageBosIkenSayfadakiGorselVeYazarBulunur() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Simyacı"/>
                  <meta property="og:image" content=""/>
                </head><body>
                  <div class="authors-wrapper">
                    <h2>Yazar: <a href="/yazar/paulo-coelho">Paulo Coelho</a></h2>
                  </div>
                  <img class="js-prd-first-image" src="/cache/500x400-0/originals/0000000064552-1.jpg"/>
                  <div class="product-description">
                    <h2>Simyacı Kitap Açıklaması</h2>
                    Kitap Açıklaması: Santiago'nun hazine arayışı...
                  </div>
                </body></html>
                """;
        BookMetadata m = og.parse(html, "https://www.dr.com.tr/kitap/simyaci/urunno=0000000064552");
        assertThat(m.title()).isEqualTo("Simyacı");
        assertThat(m.author()).isEqualTo("Paulo Coelho");
        assertThat(m.imageUrl()).isEqualTo("https://www.dr.com.tr/cache/600x600-0/originals/0000000064552-1.jpg");
        assertThat(m.description()).isEqualTo("Santiago'nun hazine arayışı...");
    }

    @Test
    void kitapyurduOgDescriptionVeYuksekCozunurlukKapak() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Vahşi Savaşçının Mutsuzluğu"/>
                  <meta property="og:image" content="https://img.kitapyurdu.com/v1/getImage/fn:195086/wh:adb8b61d7/miw:200/mih:200"/>
                  <meta property="og:description" content="Vahşi Savaşçının Mutsuzluğu - AYRINTI YAYINLARI - Pierre Clastres - Clastres bu kitabında devlet efsanesinin temellerine ışık tutuyor."/>
                </head><body>
                  <a class="ky-pd-heading__author" href="/yazar/123">Pierre Clastres</a>
                  <a class="js-jbox-book-cover" href="https://img.kitapyurdu.com/v1/getImage/fn:195086/mh:1000/wh:adb8b61d7"></a>
                </body></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.title()).isEqualTo("Vahşi Savaşçının Mutsuzluğu");
        assertThat(m.author()).isEqualTo("Pierre Clastres");
        // Sayfadaki yüksek çözünürlüklü link tercih edilir
        assertThat(m.imageUrl()).isEqualTo("https://img.kitapyurdu.com/v1/getImage/fn:195086/mh:1000/wh:adb8b61d7");
        // Başlıktaki Kitap - Yayınevi - Yazar öneki atılır, gerçek açıklama kalır
        assertThat(m.description()).isEqualTo("Clastres bu kitabında devlet efsanesinin temellerine ışık tutuyor.");
    }

    @Test
    void aciklamaninBasindakiEtiketAdlariTemizlenir() {
        String html = """
                <html><head>
                  <meta property="og:title" content="Simyacı"/>
                  <meta property="og:description" content="Arka Kapak Yazısı: İspanya'dan yola çıkan bir çobanın hikayesi."/>
                </head></html>
                """;
        BookMetadata m = og.parse(html);
        assertThat(m.description()).isEqualTo("İspanya'dan yola çıkan bir çobanın hikayesi.");
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
