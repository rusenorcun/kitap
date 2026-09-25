package app.kitappla.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
        "spring.datasource.url=jdbc:h2:mem:kitappla-sema-onarim;DB_CLOSE_DELAY=-1",
        "kitappla.seed.demo=false",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@ActiveProfiles("test")
class SemaOnarimTest {

    static {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:kitappla-sema-onarim;DB_CLOSE_DELAY=-1", "sa", "")) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
                    new org.springframework.core.io.ClassPathResource("db/migration/V1__init_schema.sql"));
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO users (id, name, email, password_hash) VALUES (101, 'Owner', 'owner@local.test', 'x'), (102, 'First', 'first@local.test', 'x'), (103, 'Next', 'next@local.test', 'x')");
                s.execute("INSERT INTO books (id, title) VALUES (101, 'History')");
                s.execute("""
                        INSERT INTO requests (id, student_id, book_id, status, fulfilled_by_id, fulfilled_at, created_at) VALUES
                        (101, 101, 101, 'OPEN', NULL, NULL, TIMESTAMP '2026-01-01 00:00:00'),
                        (102, 101, 101, 'FULFILLED', 103, TIMESTAMP '2026-07-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
                        (103, 101, 101, 'FULFILLED', 102, TIMESTAMP '2026-07-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
                        (104, 101, 101, 'FULFILLED', 102, TIMESTAMP '2026-05-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00')
                        """);
                for (long id = 101; id <= 104; id++) {
                    s.execute("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id, created_at) VALUES ("
                            + id + ", 'REQUEST', " + id + ", 101, 102, TIMESTAMP '2026-06-01 00:00:00')");
                    s.execute("INSERT INTO messages (conversation_id, sender_id, body) VALUES (" + id + ", 102, 'Private history')");
                }
                s.execute("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (105, 'CLAIM', 101, 101, 102)");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Eski şema hazırlanamadı", ex);
        }
    }

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

    @Test
    void eskiKisitliVeritabaniAcilistaOnarilirVeTekrarCalistirmaVeriyiKorur() {
        assertThat(kisitVar("uq_conversations_kind_ref")).isFalse();
        assertThat(kisitVar("uq_conversations_active_ref")).isTrue();
        assertThat(jdbc.queryForList("SELECT active_key FROM conversations ORDER BY id", Long.class))
                .containsExactly(101L, 102L, 103L, 0L, 0L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE body = 'Private history'", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE user_a_id = 101 AND user_b_id = 102", Long.class)).isEqualTo(5);
        jdbc.update("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (106, 'REQUEST', 103, 101, 102)");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (107, 'REQUEST', 103, 101, 102)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var before = jdbc.queryForList("SELECT * FROM conversations ORDER BY id");
        var history = jdbc.queryForList("SELECT * FROM messages ORDER BY id");
        onarim.run();
        onarim.run();
        assertThat(jdbc.queryForList("SELECT * FROM conversations ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM messages ORDER BY id")).isEqualTo(history);
    }

    @Test
    void onarimHatasiTohumlamadanOnceYukselir() {
        var broken = org.mockito.Mockito.mock(JdbcTemplate.class);
        org.mockito.Mockito.when(broken.execute(org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ConnectionCallback<Boolean>>any()))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"))
                .when(broken).execute(org.mockito.ArgumentMatchers.anyString());
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SemaOnarim(broken, env).run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tohumlama durduruldu");
        assertThat(SemaOnarim.class.getAnnotation(org.springframework.core.annotation.Order.class).value())
                .isLessThan(DataSeeder.class.getAnnotation(org.springframework.core.annotation.Order.class).value());
    }

    @Test
    void prodFlywayVeUpdateDisindaSohbetOnarimiCalismaz() {
        var unused = org.mockito.Mockito.mock(JdbcTemplate.class);
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update");
        new SemaOnarim(unused, env).conversationDenemesiGecerliKil();
        env.setProperty("spring.flyway.enabled", "false");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        new SemaOnarim(unused, env).conversationDenemesiGecerliKil();
        env.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        env.setActiveProfiles("prod");
        new SemaOnarim(unused, env).conversationDenemesiGecerliKil();
        org.mockito.Mockito.verifyNoInteractions(unused);
    }

    private boolean kisitVar(String ad) {
        Integer n = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                 where constraint_name = ? and table_name = 'CONVERSATIONS'
                """, Integer.class, ad.toUpperCase(Locale.ROOT));
        return n != null && n > 0;
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
