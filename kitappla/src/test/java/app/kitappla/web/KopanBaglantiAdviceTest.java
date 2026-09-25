package app.kitappla.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** İstemci kopuşları yutulur, gerçek G/Ç hataları yukarı iletilir. */
class KopanBaglantiAdviceTest {

    private final KopanBaglantiAdvice advice = new KopanBaglantiAdvice();

    @Test
    void istemciKopuslariHataSayilmaz() {
        assertThatCode(() -> advice.kopanBaglanti(new IOException("Broken pipe"))).doesNotThrowAnyException();
        assertThatCode(() -> advice.kopanBaglanti(new IOException("Connection reset by peer"))).doesNotThrowAnyException();
    }

    @Test
    void gercekGirdiCiktiHatasiYukariIletilir() {
        IOException disk = new IOException("Aygıtta yer kalmadı");
        assertThatThrownBy(() -> advice.kopanBaglanti(disk)).isSameAs(disk);
    }
}
