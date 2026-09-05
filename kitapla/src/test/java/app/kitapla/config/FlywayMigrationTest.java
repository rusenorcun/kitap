package app.kitapla.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway migrasyonlarının sıfırdan başarıyla uygulandığını ve
 * Hibernate varlıklarının {@code ddl-auto=validate} ile şemaya tam uyduğunu doğrular.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kitapla-flyway-test;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "kitapla.seed.demo=false"
})
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigrasyonlariBasariylaUygulanir() {
        var info = flyway.info().current();
        assertThat(info).as("Flyway migrasyonu uygulanmış olmalı").isNotNull();
        assertThat(info.getVersion().getVersion()).isEqualTo("1");
        assertThat(info.getDescription()).isEqualTo("init schema");
        assertThat(info.getState().isApplied()).isTrue();
    }

    @Test
    void tumTemelTablolarOlusturulur() {
        Integer userTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name = 'USERS'",
                Integer.class
        );
        assertThat(userTableCount).isGreaterThan(0);

        Integer donationsTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name = 'DONATIONS'",
                Integer.class
        );
        assertThat(donationsTableCount).isGreaterThan(0);

        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(1);
    }
}
