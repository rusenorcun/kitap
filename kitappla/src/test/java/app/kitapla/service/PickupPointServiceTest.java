package app.kitapla.service;

import app.kitapla.domain.PickupPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kampüs teslim noktalarının yönetimi. */
@SpringBootTest
@ActiveProfiles("test")
class PickupPointServiceTest {

    @Autowired PickupPointService points;

    private String tekil() {
        return "N-" + UUID.randomUUID();
    }

    @Test
    void noktaEklenirVeAktifListedeGorunur() {
        String ad = tekil();
        PickupPoint p = points.create("Tınaztepe", ad, "Turnikelerin solu");

        assertThat(p.isActive()).isTrue();
        assertThat(p.getFullName()).isEqualTo("Tınaztepe — " + ad);
        assertThat(points.active()).anyMatch(x -> x.getId().equals(p.getId()));
    }

    @Test
    void ayniKampusteAyniAdIkinciKezEklenemez() {
        String ad = tekil();
        points.create("Merkez", ad, null);

        assertThatThrownBy(() -> points.create("merkez", ad.toLowerCase(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten var");
    }

    @Test
    void farkliKampusteAyniAdOlabilir() {
        String ad = tekil();
        points.create("Kampüs A", ad, null);
        assertThat(points.create("Kampüs B", ad, null)).isNotNull();
    }

    @Test
    void bosAlanlarReddedilir() {
        assertThatThrownBy(() -> points.create("  ", "Bir yer", null))
                .hasMessageContaining("Kampüs adı zorunlu");
        assertThatThrownBy(() -> points.create("Kampüs", "   ", null))
                .hasMessageContaining("Nokta adı zorunlu");
    }

    @Test
    void pasiflestirilenNoktaSecilemezAmaKaydiDurur() {
        PickupPoint p = points.create("Pasif Kampüs", tekil(), null);
        points.setActive(p.getId(), false);

        assertThat(points.active()).noneMatch(x -> x.getId().equals(p.getId()));
        assertThat(points.findSelectable(p.getId())).isEmpty();
        // Kayıt silinmez: geçmiş buluşmalar bu noktaya bağlı olabilir
        assertThat(points.find(p.getId())).isPresent();
        assertThat(points.all()).anyMatch(x -> x.getId().equals(p.getId()));
    }

    @Test
    void pasifNoktaGeriAcilir() {
        PickupPoint p = points.create("Geri Kampüs", tekil(), null);
        points.setActive(p.getId(), false);
        points.setActive(p.getId(), true);

        assertThat(points.findSelectable(p.getId())).isPresent();
        assertThatThrownBy(() -> points.setActive(p.getId(), true))
                .hasMessageContaining("zaten aktif");
    }

    @Test
    void guncellemeCakismayiEngeller() {
        String a = tekil(), b = tekil();
        points.create("Çakışma", a, null);
        PickupPoint ikinci = points.create("Çakışma", b, null);

        assertThatThrownBy(() -> points.update(ikinci.getId(), "Çakışma", a, null))
                .hasMessageContaining("zaten var");

        // Kendi adıyla güncelleme sorun çıkarmamalı
        assertThat(points.update(ikinci.getId(), "Çakışma", b, "yeni tarif").getDescription())
                .isEqualTo("yeni tarif");
    }
}
