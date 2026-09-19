package app.kitapla.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Şikâyet formundaki "geri" adresi yalnızca uygulama içine yönlendirebilir. */
class SikayetYonlendirmeTest {

    @ParameterizedTest
    @ValueSource(strings = {"//evil.example", "https://evil.example", "/\t/evil.example",
            "/\n/evil.example", "/\r\n/evil.example", "/\\evil.example", "evil.example", "/ /evil.example"})
    void disAdreslerPanoyaDuser(String geri) {
        assertThat(ReportController.icAdres(geri)).isEqualTo("/panom");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mesajlar/5", "/kesfet/12", "/takas/teklifler/3"})
    void uygulamaIciAdreslerKorunur(String geri) {
        assertThat(ReportController.icAdres(geri)).isEqualTo(geri);
    }
}
