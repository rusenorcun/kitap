package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRequestRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookService;
import app.kitapla.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** İstek sayfaları ve adres gizliliği. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRequestRepository requests;
    @Autowired BookService bookService;
    @Autowired RequestService requestService;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Web " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    @Test
    void acikIsteklerSayfasiAnonimeAcilir() throws Exception {
        User isteyen = mk("acik", "Ankara Gizli Sokak 7");
        requestService.create(isteyen, bookService.findOrCreate("Anonim Test Kitabı", "Y", null, null, null, null), null);

        mvc.perform(get("/istekler"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anonim Test Kitabı")))
                .andExpect(content().string(containsString("Karşılamak için giriş yap")));
    }

    @Test
    void acikIsteklerdeTeslimatAdresiGORUNMEZ() throws Exception {
        User isteyen = mk("gizli", "Ankara Gizli Sokak 7");
        requestService.create(isteyen, bookService.findOrCreate("Gizlilik Kitabı", "Y", null, null, null, null), null);

        mvc.perform(get("/istekler"))
                .andExpect(content().string(not(containsString("Gizli Sokak 7"))));
    }

    @Test
    void istekOlusturulurVeIsteklerimdeGorunur() throws Exception {
        User isteyen = mk("olustur", "İzmir");

        mvc.perform(post("/istek/yeni").with(user(as(isteyen))).with(csrf())
                        .param("title", "Form Testi Kitabı")
                        .param("author", "Form Yazar")
                        .param("description", "Ödev için"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/isteklerim"));

        assertThat(requests.findByStudentWithDetails(isteyen)).hasSize(1);
        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Form Testi Kitabı")))
                .andExpect(content().string(containsString("Açık · bağışçı bekleniyor")));
    }

    @Test
    void baslikYoksaHataGosterilir() throws Exception {
        User isteyen = mk("hatali", "İzmir");
        mvc.perform(post("/istek/yeni").with(user(as(isteyen))).with(csrf()).param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("istek-yeni"))
                .andExpect(model().attributeExists("hata"));
    }

    @Test
    void kampusTeslimindeIsteyenAdresiGosterilmez() throws Exception {
        User isteyen = mk("adresli", "Ankara Çankaya 99");
        User karsilayan = mk("karsilayan", "İzmir");
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate("Adres Testi " + UUID.randomUUID(), "Y", null, null, null, null), null);

        mvc.perform(post("/istek/" + r.getId() + "/karsila").with(user(as(karsilayan))).with(csrf())
                        .param("source", "PURCHASE"))
                .andExpect(redirectedUrl("/karsiladiklarim"));

        // Yüz yüze teslimde adres paylaşılmaz (adres akışı: KargoModuSayfaTest)
        mvc.perform(get("/karsiladiklarim").with(user(as(karsilayan))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))))
                .andExpect(content().string(not(containsString("Kargoya verdim"))))
                .andExpect(content().string(containsString("Buluşma ayarla")));

        // Üçüncü bir kişi görmez
        User yabanci = mk("yabanci", "Bursa");
        mvc.perform(get("/karsiladiklarim").with(user(as(yabanci))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))));
    }

    @Test
    void isteklerimdeTeslimAldimGorunur() throws Exception {
        User isteyen = mk("teslim", "Ankara");
        User karsilayan = mk("karsilayan2", "İzmir");
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate("Teslim Testi " + UUID.randomUUID(), "Y", null, null, null, null), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        // Yüz yüze teslimde önce buluşma ayarlanır
        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Buluşma ayarla")));

        mvc.perform(post("/bulusma/istek/" + r.getId()).with(user(as(isteyen))).with(csrf())
                        .param("note", "Yemekhane önü")
                        .param("at", java.time.LocalDateTime.now().plusDays(1)
                                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()))
                .andExpect(flash().attributeExists("basari"));

        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Teslim aldım")))
                .andExpect(content().string(containsString("Yemekhane önü")));
    }

    @Test
    void istekOlusturmaGirisIster() throws Exception {
        mvc.perform(get("/istek/yeni"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
