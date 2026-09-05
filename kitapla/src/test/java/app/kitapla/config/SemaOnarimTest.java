package app.kitapla.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SemaOnarim}'ın hangi sütunları onaracağını doğrular.
 *
 * <p>Onarım, tablo ya da sütun bulunamadığında hatayı yutar — ilk kurulumda onarılacak
 * bir şey olmaması normaldir. Bunun bedeli, yanlış türetilmiş bir tablo/sütun adının da
 * sessizce atlanmasıdır. Bu test o riski kapatır: türetilen her adın veritabanında
 * gerçekten karşılığı olduğunu sorgulayarak doğrular.</p>
 */
// Kendi veritabani: paylasilan bellek-ici H2'de create-drop teardown'i,
// information_schema sorgusuyla yarisip tablolari yariya inmis gosterebiliyor.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kitapla-sema-onarim;DB_CLOSE_DELAY=-1",
        "kitapla.seed.demo=false"
})
@ActiveProfiles("test")
class SemaOnarimTest {

    @Autowired SemaOnarim onarim;
    @Autowired JdbcTemplate jdbc;

    @Test
    void columnDefaultTasiyanHerAlanKapsanir() {
        // Varlıklardaki @ColumnDefault sayısı; yeni alan eklendiğinde bu test değil,
        // onarımın kendisi kendiliğinden büyür — burada yalnızca boş kalmadığını doğularız.
        assertThat(onarim.sutunlar())
                .as("domain paketi taranabilmeli")
                .isNotEmpty();
    }

    @Test
    void turetilenTumTabloVeSutunAdlariGercekten() {
        List<String> bulunamayan = onarim.sutunlar().stream()
                .filter(s -> !sutunVar(s.tablo(), s.ad()))
                .map(s -> s.tablo() + "." + s.ad())
                .toList();

        assertThat(bulunamayan)
                .as("türetilen adlar şemayla birebir eşleşmeli; eşleşmezse onarım sessizce atlar")
                .isEmpty();
    }

    @Test
    void bilinenBozukSutunlarListedeYerAlir() {
        List<String> adlar = onarim.sutunlar().stream()
                .map(s -> s.tablo() + "." + s.ad())
                .toList();

        // Üretimde siteyi düşüren iki sütun; kapsamdan çıkmadıklarından emin ol.
        assertThat(adlar).contains("users.blocked", "users.no_show_count");
    }

    /**
     * H2'nin kendi INFORMATION_SCHEMA.USERS sistem tablosu, uygulamanın users tablosuyla
     * aynı adı taşır; bu yüzden arama PUBLIC şemasıyla sınırlanır.
     */
    private boolean sutunVar(String tablo, String sutun) {
        // Adlar Java tarafinda buyutulur: H2, upper(?) icinde parametre tipini
        // cikaramayip sessizce eslesmeyen sonuc dondurebiliyor.
        Integer n = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'PUBLIC'
                   and table_name = ?
                   and column_name = ?
                """, Integer.class,
                tablo.toUpperCase(Locale.ROOT), sutun.toUpperCase(Locale.ROOT));
        return n != null && n > 0;
    }
}
