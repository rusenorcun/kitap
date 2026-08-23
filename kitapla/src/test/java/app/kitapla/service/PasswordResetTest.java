package app.kitapla.service;

import app.kitapla.domain.TokenType;
import app.kitapla.domain.User;
import app.kitapla.mail.MailService;
import app.kitapla.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Şifre sıfırlama akışı ve jeton güvenliği. */
@SpringBootTest
@ActiveProfiles("test")
class PasswordResetTest {

    @Autowired PasswordResetService service;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MailService mail;

    @BeforeEach
    void temizle() {
        mail.clearOutbox();
    }

    private User mk(String tag) {
        User u = new User();
        u.setName("Sifirlama " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("eskisifre1"));
        return users.save(u);
    }

    /** Gönderilen postadaki bağlantıdan ham jetonu çıkarır. */
    private String sonJeton() {
        var son = mail.outbox().get(mail.outbox().size() - 1);
        int i = son.html().indexOf("/sifre-sifirla?token=");
        assertThat(i).as("postada sıfırlama bağlantısı olmalı").isGreaterThan(-1);
        String kuyruk = son.html().substring(i + "/sifre-sifirla?token=".length());
        return kuyruk.split("[\"'<\\s]")[0];
    }

    @Test
    void sifirlamaBaglantisiGonderilirVeSifreDegisir() {
        User u = mk("akis");
        service.request(u.getEmail());

        assertThat(mail.outbox()).hasSize(1);
        assertThat(mail.outbox().get(0).to()).isEqualTo(u.getEmail());
        assertThat(mail.outbox().get(0).subject()).contains("Şifre sıfırlama");

        String jeton = sonJeton();
        assertThat(service.isValid(jeton)).isTrue();

        service.reset(jeton, "yenisifre1", "yenisifre1");

        assertThat(encoder.matches("yenisifre1",
                users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void jetonYalnizcaBirKezKullanilir() {
        User u = mk("tekkullanim");
        service.request(u.getEmail());
        String jeton = sonJeton();

        service.reset(jeton, "yenisifre1", "yenisifre1");

        assertThatThrownBy(() -> service.reset(jeton, "baskasifre2", "baskasifre2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geçersiz");
        // İkinci deneme şifreyi değiştirmemeli
        assertThat(encoder.matches("yenisifre1",
                users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void yeniIstekEskiBaglantiyiGecersizKilar() {
        User u = mk("yenile");
        service.request(u.getEmail());
        String eski = sonJeton();

        service.request(u.getEmail());
        String yeni = sonJeton();

        assertThat(eski).isNotEqualTo(yeni);
        assertThat(service.isValid(eski)).as("eski bağlantı ölmeli").isFalse();
        assertThat(service.isValid(yeni)).isTrue();
    }

    @Test
    void kayitsizAdresIcinPostaGitmezVeHataVerilmez() {
        service.request("hicyok-" + UUID.randomUUID() + "@test.local");
        assertThat(mail.outbox()).isEmpty();
    }

    @Test
    void askidakiHesapIcinBaglantiGonderilmez() {
        User u = mk("askida");
        u.setBlocked(true);
        users.save(u);

        service.request(u.getEmail());
        assertThat(mail.outbox()).isEmpty();
    }

    @Test
    void kisaVeEslesmeyenSifreReddedilir() {
        User u = mk("dogrulama");
        service.request(u.getEmail());
        String jeton = sonJeton();

        assertThatThrownBy(() -> service.reset(jeton, "kisa", "kisa"))
                .hasMessageContaining("en az 6 karakter");
        assertThatThrownBy(() -> service.reset(jeton, "yenisifre1", "baskasey2"))
                .hasMessageContaining("eşleşmiyor");

        // Başarısız denemeler jetonu harcamamalı
        assertThat(service.isValid(jeton)).isTrue();
    }

    @Test
    void uydurulmusJetonKabulEdilmez() {
        assertThat(service.isValid("tamamen-uydurma-jeton")).isFalse();
        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("")).isFalse();
    }

    @Test
    void jetonVeritabaninaDuzMetinYazilmaz() {
        User u = mk("ozet");
        service.request(u.getEmail());
        String jeton = sonJeton();

        var kayit = tokens.verify(jeton, TokenType.PASSWORD_RESET).orElseThrow();
        assertThat(kayit.getTokenHash())
                .as("yalnızca SHA-256 özeti saklanmalı")
                .isNotEqualTo(jeton)
                .hasSize(64);
    }

    @Test
    void cokFazlaIstekSinirlanir() {
        User u = mk("sinir");
        for (int i = 0; i < 5; i++) service.request(u.getEmail());

        assertThatThrownBy(() -> service.request(u.getEmail()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Çok fazla istek");
    }

    @Test
    void sifreDegisinceBilgilendirmePostasiGider() {
        User u = mk("bilgi");
        service.request(u.getEmail());
        service.reset(sonJeton(), "yenisifre1", "yenisifre1");

        assertThat(mail.outbox()).hasSize(2);
        assertThat(mail.outbox().get(1).subject()).contains("Şifren değiştirildi");
    }
}
