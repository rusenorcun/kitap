package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Keşfet ve kitap detayı sayfalarının gerçekten render olduğunu doğrular.
 * (open-in-view kapalı olduğu için lazy ilişkilerin fetch-join ile geldiğini de kapsar.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;

    private Donation seeded;

    @BeforeEach
    void seed() {
        if (seeded != null) return;
        User donor = new User();
        donor.setName("Sayfa Bağışçı");
        donor.setEmail("sayfa-" + UUID.randomUUID() + "@test.local");
        donor.setPasswordHash("x");
        donor.setAddress("Adres");
        donor = users.save(donor);

        Book b = new Book();
        b.setTitle("Katalog Test Kitabı");
        b.setAuthor("Katalog Yazar");
        b = books.save(b);

        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(b);
        d.setQuantity(2);
        d.setTargetLevel(TargetLevel.HEPSI);
        seeded = donations.save(d);
    }

    @Test
    void kesfetSayfasiAnonimKullaniciyaAcilir() throws Exception {
        mvc.perform(get("/kesfet"))
                .andExpect(status().isOk())
                .andExpect(view().name("kesfet"))
                .andExpect(content().string(containsString("Katalog Test Kitabı")))
                .andExpect(content().string(containsString("Bağışları keşfet")));
    }

    @Test
    void htmxParcasiYalnizcaIzgaraDondurur() throws Exception {
        mvc.perform(get("/kesfet/liste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Katalog Test Kitabı")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void aramaFiltresiSonucuDaraltir() throws Exception {
        mvc.perform(get("/kesfet/liste").param("q", "Katalog Test"))
                .andExpect(content().string(containsString("Katalog Test Kitabı")));

        mvc.perform(get("/kesfet/liste").param("q", "boyle-bir-kitap-yok"))
                .andExpect(content().string(containsString("Sonuç bulunamadı")));
    }

    @Test
    void kitapDetayiAcilirVeOncelikDurumunuGosterir() throws Exception {
        mvc.perform(get("/kitap/" + seeded.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("kitap-detay"))
                .andExpect(content().string(containsString("Katalog Test Kitabı")))
                .andExpect(content().string(containsString("Öğrenci önceliği")));
    }

    @Test
    void kitapDetayiGIRIS_YAPMIS_kullaniciya_da_acilir() throws Exception {
        // Sayfada sec:authorize="isAuthenticated()" ile gösterilen bölümler var.
        // Yalnızca anonim istekle test edilirse o bölümlerdeki hatalar görünmez —
        // bir kez gerçekten öyle bir hata kaçmıştı (şikâyet bağlantısı 500 veriyordu).
        User uye = new User();
        uye.setName("Katalog Üye");
        uye.setEmail("katalog-" + UUID.randomUUID() + "@test.local");
        uye.setPasswordHash("x");
        uye.setAddress("İzmir");
        uye = users.save(uye);

        mvc.perform(get("/kitap/" + seeded.getId())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(new AppUserDetails(uye))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Katalog Test Kitabı")))
                .andExpect(content().string(containsString("şikâyet et")));
    }

    @Test
    void anonimKullaniciyaGirisYapUyarisiGosterilir() throws Exception {
        mvc.perform(get("/kitap/" + seeded.getId()))
                .andExpect(content().string(containsString("Almak için giriş yap")));
    }

    @Test
    void olmayanKitapIcin404() throws Exception {
        mvc.perform(get("/kitap/999999"))
                .andExpect(status().isNotFound());
    }
}
